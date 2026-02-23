package com.odin.wms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.ShippingOrder.ShippingStatus;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.ShippingItemResponse;
import com.odin.wms.dto.response.ShippingManifestResponse;
import com.odin.wms.dto.response.ShippingOrderResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.ShippingCompletedEventPublisher;
import com.odin.wms.messaging.event.PackingCompletedEvent;
import com.odin.wms.messaging.event.ShippingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    static final UUID SYSTEM_OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ShippingOrderRepository shippingOrderRepository;
    private final ShippingItemRepository shippingItemRepository;
    private final PackingItemRepository packingItemRepository;
    private final ProductWmsRepository productWmsRepository;
    private final LotRepository lotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogIndexer auditLogIndexer;
    private final ShippingCompletedEventPublisher shippingCompletedEventPublisher;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // AC3 — Kafka: criar ShippingOrder a partir de PackingCompletedEvent
    // =========================================================================

    @Transactional
    public void createShippingOrderFromKafka(PackingCompletedEvent event) {
        UUID tenantId = event.tenantId();
        UUID packingOrderId = event.packingOrderId();

        // Idempotência: se já existe, descarta silenciosamente
        if (shippingOrderRepository.findByTenantIdAndPackingOrderId(tenantId, packingOrderId).isPresent()) {
            log.warn("ShippingOrder já existe para packingOrderId={} (tenant={}) — evento ignorado",
                    packingOrderId, tenantId);
            return;
        }

        ShippingOrder order = ShippingOrder.builder()
                .tenantId(tenantId)
                .packingOrderId(packingOrderId)
                .pickingOrderId(event.pickingOrderId())
                .crmOrderId(event.crmOrderId())
                .warehouseId(event.warehouseId())
                .status(ShippingStatus.PENDING)
                .build();

        ShippingOrder savedOrder = shippingOrderRepository.save(order);

        // Carregar packing items para obter packingItemId (não vem no evento)
        List<PackingItem> packingItems = packingItemRepository
                .findByTenantIdAndPackingOrderId(tenantId, packingOrderId);

        // Construir mapa (productId, lotId) → PackingItem para lookup
        Map<String, PackingItem> packingItemMap = packingItems.stream()
                .collect(Collectors.toMap(
                        pi -> pi.getProductId() + ":" + pi.getLotId(),
                        pi -> pi,
                        (a, b) -> a // manter primeiro em caso de duplicata (improvável)
                ));

        if (event.items() != null && !event.items().isEmpty()) {
            List<ShippingItem> items = event.items().stream()
                    .map(item -> {
                        String key = item.productId() + ":" + item.lotId();
                        PackingItem packingItem = packingItemMap.get(key);
                        if (packingItem == null) {
                            log.warn("PackingItem não encontrado para productId={} lotId={} em packingOrderId={}",
                                    item.productId(), item.lotId(), packingOrderId);
                            return null;
                        }
                        return ShippingItem.builder()
                                .tenantId(tenantId)
                                .shippingOrderId(savedOrder.getId())
                                .packingItemId(packingItem.getId())
                                .productId(item.productId())
                                .lotId(item.lotId())
                                .quantityShipped(item.quantityPacked())
                                .loaded(false)
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            shippingItemRepository.saveAll(items);
        } else {
            log.warn("PackingCompletedEvent sem itens para packingOrderId={}", packingOrderId);
        }

        log.info("ShippingOrder {} criada via Kafka (packingOrderId={} tenant={})",
                savedOrder.getId(), packingOrderId, tenantId);
    }

    // =========================================================================
    // AC4 — startLoading: PENDING → IN_PROGRESS
    // =========================================================================

    @Transactional
    public ShippingOrderResponse startLoading(UUID orderId, StartLoadingRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ShippingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != ShippingStatus.PENDING) {
            throw new ConflictException(
                    "Shipping order não está em PENDING. Status atual: " + order.getStatus());
        }

        order.setStatus(ShippingStatus.IN_PROGRESS);
        order.setOperatorId(request.operatorId());
        ShippingOrder saved = shippingOrderRepository.save(order);

        log.info("ShippingOrder {} iniciada pelo operador {} (tenant={})",
                orderId, request.operatorId(), tenantId);

        List<ShippingItemResponse> itemResponses = loadItemResponses(tenantId, orderId);
        return ShippingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC5 — loadItem: confirmar carregamento via scanner
    // =========================================================================

    @Transactional
    public ShippingItemResponse loadItem(UUID orderId, UUID itemId, LoadItemRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ShippingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != ShippingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Shipping order não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        ShippingItem item = shippingItemRepository.findByIdAndTenantId(itemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ShippingItem não encontrado: " + itemId));

        if (item.isLoaded()) {
            throw new BusinessException("Item " + itemId + " já foi carregado");
        }

        ProductWms product = productWmsRepository.findByIdAndTenantId(item.getProductId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto não encontrado: " + item.getProductId()));

        String barcode = request.barcode();
        boolean matchesSku = barcode.equalsIgnoreCase(product.getSku());
        boolean matchesLot = false;

        if (!matchesSku && item.getLotId() != null) {
            matchesLot = lotRepository.findById(item.getLotId())
                    .map(lot -> barcode.equalsIgnoreCase(lot.getLotNumber()))
                    .orElse(false);
        }

        if (!matchesSku && !matchesLot) {
            throw new BusinessException(
                    "Barcode " + barcode + " não corresponde ao produto ou lote esperado para o item " + itemId);
        }

        item.setLoaded(true);
        item.setLoadedAt(Instant.now());
        item.setLoadedBy(extractOperatorId());
        ShippingItem saved = shippingItemRepository.save(item);

        log.info("ShippingItem {} carregado (shippingOrder={} tenant={})", itemId, orderId, tenantId);
        return ShippingItemResponse.from(saved);
    }

    // =========================================================================
    // AC6 — setCarrierDetails: registrar dados da transportadora
    // =========================================================================

    @Transactional
    public ShippingOrderResponse setCarrierDetails(UUID orderId, SetCarrierDetailsRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ShippingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != ShippingStatus.PENDING && order.getStatus() != ShippingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Shipping order não está em PENDING ou IN_PROGRESS. Status atual: " + order.getStatus());
        }

        if (request.carrierName() != null) order.setCarrierName(request.carrierName());
        if (request.vehiclePlate() != null) order.setVehiclePlate(request.vehiclePlate());
        if (request.driverName() != null) order.setDriverName(request.driverName());
        if (request.trackingNumber() != null) order.setTrackingNumber(request.trackingNumber());
        if (request.estimatedDelivery() != null) order.setEstimatedDelivery(request.estimatedDelivery());

        ShippingOrder saved = shippingOrderRepository.save(order);
        log.info("ShippingOrder {} dados de transportadora atualizados (tenant={})", orderId, tenantId);
        return ShippingOrderResponse.from(saved, null);
    }

    // =========================================================================
    // AC7 — generateManifest: gerar manifesto de carga (idempotente)
    // =========================================================================

    @Transactional
    public ShippingManifestResponse generateManifest(UUID orderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ShippingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != ShippingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Shipping order não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        // Idempotência: retornar manifesto existente sem regenerar
        if (order.getManifestJson() != null && order.getManifestGeneratedAt() != null) {
            List<ShippingItem> items = shippingItemRepository
                    .findByTenantIdAndShippingOrderId(tenantId, orderId);
            log.info("ShippingOrder {} manifesto existente retornado (tenant={})", orderId, tenantId);
            return new ShippingManifestResponse(orderId, order.getManifestJson(),
                    order.getManifestGeneratedAt(), items.size());
        }

        List<ShippingItem> items = shippingItemRepository
                .findByTenantIdAndShippingOrderId(tenantId, orderId);

        Instant generatedAt = Instant.now();
        ManifestDto manifestDto = buildManifestDto(order, items, generatedAt);

        String manifestJson;
        try {
            manifestJson = objectMapper.writeValueAsString(manifestDto);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Erro ao serializar manifesto: " + e.getMessage());
        }

        order.setManifestJson(manifestJson);
        order.setManifestGeneratedAt(generatedAt);
        shippingOrderRepository.save(order);

        log.info("ShippingOrder {} manifesto gerado ({} itens tenant={})", orderId, items.size(), tenantId);
        return new ShippingManifestResponse(orderId, manifestJson, generatedAt, items.size());
    }

    // =========================================================================
    // AC8 — dispatchShipping: despachar envio
    // =========================================================================

    @Transactional
    public ShippingOrderResponse dispatchShipping(UUID orderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ShippingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != ShippingStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Shipping order não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        if (order.getCarrierName() == null) {
            throw new BusinessException(
                    "Dados da transportadora não informados. Chame set-carrier-details antes de despachar.");
        }

        List<ShippingItem> items = shippingItemRepository
                .findByTenantIdAndShippingOrderId(tenantId, orderId);

        List<UUID> unloadedItemIds = items.stream()
                .filter(i -> !i.isLoaded())
                .map(ShippingItem::getId)
                .toList();

        if (!unloadedItemIds.isEmpty()) {
            throw new BusinessException("Existem itens não carregados: " + unloadedItemIds);
        }

        UUID actorId = extractOperatorId();

        order.setStatus(ShippingStatus.DISPATCHED);
        order.setDispatchedAt(Instant.now());
        ShippingOrder saved = shippingOrderRepository.save(order);

        // AuditLog
        String newValue = String.format(
                "{\"shippingOrderId\":\"%s\",\"status\":\"DISPATCHED\",\"carrierName\":\"%s\"}",
                orderId, order.getCarrierName());
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("SHIPPING_ORDER")
                .entityId(orderId)
                .action(AuditAction.MOVEMENT)
                .actorId(actorId)
                .newValue(newValue)
                .build();
        AuditLog savedLog = auditLogRepository.save(auditLog);
        auditLogIndexer.indexAuditLogAsync(savedLog);

        // StockMovements OUTBOUND — um por ShippingItem
        for (ShippingItem item : items) {
            ProductWms product = productWmsRepository.findByIdAndTenantId(item.getProductId(), tenantId)
                    .orElse(null);
            if (product == null) {
                log.warn("Produto {} não encontrado ao criar StockMovement OUTBOUND para item {}",
                        item.getProductId(), item.getId());
                continue;
            }

            Lot lot = null;
            if (item.getLotId() != null) {
                lot = lotRepository.findById(item.getLotId()).orElse(null);
            }

            StockMovement movement = StockMovement.builder()
                    .tenantId(tenantId)
                    .type(MovementType.SHIPPING)
                    .product(product)
                    .lot(lot)
                    .quantity(item.getQuantityShipped())
                    .referenceType(ReferenceType.SHIPPING_ORDER)
                    .referenceId(orderId)
                    .operatorId(actorId)
                    .build();
            stockMovementRepository.save(movement);
        }

        // Publicar ShippingCompletedEvent
        List<ShippingCompletedEvent.ShippingCompletedItem> eventItems = items.stream()
                .map(item -> new ShippingCompletedEvent.ShippingCompletedItem(
                        item.getId(), item.getProductId(), item.getLotId(), item.getQuantityShipped()))
                .toList();

        ShippingCompletedEvent event = new ShippingCompletedEvent(
                "SHIPPING_ORDER_DISPATCHED",
                tenantId,
                orderId,
                order.getPackingOrderId(),
                order.getPickingOrderId(),
                order.getCrmOrderId(),
                order.getWarehouseId(),
                order.getCarrierName(),
                order.getTrackingNumber(),
                order.getEstimatedDelivery(),
                order.getOperatorId() != null ? order.getOperatorId() : actorId,
                eventItems,
                saved.getDispatchedAt()
        );
        shippingCompletedEventPublisher.publishShippingCompleted(event);

        log.info("ShippingOrder {} despachada (tenant={})", orderId, tenantId);

        List<ShippingItemResponse> itemResponses = items.stream()
                .map(ShippingItemResponse::from)
                .toList();
        return ShippingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC9 — cancelShipping: cancelar PENDING ou IN_PROGRESS
    // =========================================================================

    @Transactional
    public ShippingOrderResponse cancelShipping(UUID orderId, CancelShippingOrderRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ShippingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() == ShippingStatus.DISPATCHED
                || order.getStatus() == ShippingStatus.DELIVERED) {
            throw new ConflictException(
                    "Não é possível cancelar uma ordem no status " + order.getStatus());
        }
        if (order.getStatus() == ShippingStatus.CANCELLED) {
            throw new ConflictException("Shipping order já está cancelada");
        }

        UUID actorId = extractOperatorId();

        order.setStatus(ShippingStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancellationReason(request.cancellationReason());
        ShippingOrder saved = shippingOrderRepository.save(order);

        // AuditLog
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("SHIPPING_ORDER")
                .entityId(orderId)
                .action(AuditAction.UPDATE)
                .actorId(actorId)
                .newValue(String.format(
                        "{\"shippingOrderId\":\"%s\",\"status\":\"CANCELLED\",\"reason\":\"%s\"}",
                        orderId, request.cancellationReason()))
                .build();
        auditLogRepository.save(auditLog);

        log.info("ShippingOrder {} cancelada (tenant={})", orderId, tenantId);

        List<ShippingItemResponse> itemResponses = loadItemResponses(tenantId, orderId);
        return ShippingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC10 — getOrder: consulta com isolamento de tenant
    // =========================================================================

    @Transactional(readOnly = true)
    public ShippingOrderResponse getOrder(UUID orderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ShippingOrder order = getOrderForTenant(orderId, tenantId);
        List<ShippingItemResponse> items = loadItemResponses(tenantId, orderId);
        return ShippingOrderResponse.from(order, items);
    }

    // =========================================================================
    // AC11 — listOrders: listagem paginada com filtro opcional de status
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<ShippingOrderResponse> listOrders(ShippingStatus status, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Page<ShippingOrder> page;
        if (status != null) {
            page = shippingOrderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = shippingOrderRepository.findByTenantId(tenantId, pageable);
        }
        // Listagem não inclui itens (AC11)
        return page.map(order -> ShippingOrderResponse.from(order, null));
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    private ShippingOrder getOrderForTenant(UUID orderId, UUID tenantId) {
        return shippingOrderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> {
                    if (shippingOrderRepository.existsById(orderId)) {
                        return new AccessDeniedException(
                                "Acesso negado à shipping order: " + orderId);
                    }
                    return new ResourceNotFoundException(
                            "Shipping order não encontrada: " + orderId);
                });
    }

    private List<ShippingItemResponse> loadItemResponses(UUID tenantId, UUID orderId) {
        return shippingItemRepository
                .findByTenantIdAndShippingOrderId(tenantId, orderId)
                .stream()
                .map(ShippingItemResponse::from)
                .toList();
    }

    private UUID extractOperatorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException ignored) {
                    // fall through
                }
            }
        }
        return SYSTEM_OPERATOR_ID;
    }

    private ManifestDto buildManifestDto(ShippingOrder order, List<ShippingItem> items, Instant generatedAt) {
        List<ManifestItemDto> manifestItems = items.stream()
                .map(item -> new ManifestItemDto(
                        item.getId(), item.getProductId(), item.getLotId(),
                        item.getQuantityShipped(), item.isLoaded()))
                .toList();

        return new ManifestDto(
                order.getId(),
                order.getPackingOrderId(),
                order.getCrmOrderId(),
                order.getWarehouseId(),
                order.getTenantId(),
                order.getCarrierName(),
                order.getVehiclePlate(),
                order.getDriverName(),
                order.getTrackingNumber(),
                order.getEstimatedDelivery(),
                generatedAt,
                items.size(),
                manifestItems
        );
    }

    // DTO internos para serialização do manifesto
    private record ManifestDto(
            UUID shippingOrderId,
            UUID packingOrderId,
            UUID crmOrderId,
            UUID warehouseId,
            UUID tenantId,
            String carrierName,
            String vehiclePlate,
            String driverName,
            String trackingNumber,
            java.time.LocalDate estimatedDelivery,
            Instant generatedAt,
            int itemCount,
            List<ManifestItemDto> items
    ) {}

    private record ManifestItemDto(
            UUID shippingItemId,
            UUID productId,
            UUID lotId,
            int quantityShipped,
            boolean loaded
    ) {}
}
