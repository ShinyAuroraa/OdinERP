package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PickingItem.PickingItemStatus;
import com.odin.wms.domain.entity.PickingOrder.PickingOrderType;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.entity.ProductionMaterialRequest.MaterialRequestStatus;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.ReceiveFinishedGoodsRequest;
import com.odin.wms.dto.response.ProductionMaterialRequestItemResponse;
import com.odin.wms.dto.response.ProductionMaterialRequestResponse;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.FinishedGoodsReceivedEventPublisher;
import com.odin.wms.messaging.MaterialsDeliveredEventPublisher;
import com.odin.wms.messaging.StockShortageEventPublisher;
import com.odin.wms.messaging.event.FinishedGoodsReceivedEvent;
import com.odin.wms.messaging.event.MaterialsDeliveredEvent;
import com.odin.wms.messaging.event.MrpProductionOrderCancelledEvent;
import com.odin.wms.messaging.event.MrpProductionOrderReleasedEvent;
import com.odin.wms.messaging.event.StockShortageEvent;
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
public class ProductionIntegrationService {

    static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ProductionMaterialRequestRepository requestRepository;
    private final ProductionMaterialRequestItemRepository itemRepository;
    private final PickingOrderRepository pickingOrderRepository;
    private final PickingItemRepository pickingItemRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final LocationRepository locationRepository;
    private final ProductWmsRepository productWmsRepository;
    private final LotRepository lotRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogIndexer auditLogIndexer;
    private final MaterialsDeliveredEventPublisher materialsDeliveredEventPublisher;
    private final FinishedGoodsReceivedEventPublisher finishedGoodsReceivedEventPublisher;
    private final StockShortageEventPublisher stockShortageEventPublisher;

    // =========================================================================
    // AC4 — Saga: processar MrpProductionOrderReleasedEvent
    // =========================================================================

    @Transactional
    public void processProductionOrderReleased(MrpProductionOrderReleasedEvent event) {
        UUID tenantId = event.tenantId();
        UUID productionOrderId = event.productionOrderId();

        // Idempotência
        if (requestRepository.existsByTenantIdAndProductionOrderId(tenantId, productionOrderId)) {
            log.info("ProductionMaterialRequest já existe para productionOrderId={} (tenant={}) — evento ignorado",
                    productionOrderId, tenantId);
            return;
        }

        // Validar componentes
        if (event.components() == null || event.components().isEmpty()) {
            log.warn("Evento PRODUCTION_ORDER_RELEASED sem componentes para OP {} — status ERROR", productionOrderId);
            ProductionMaterialRequest errRequest = ProductionMaterialRequest.builder()
                    .tenantId(tenantId)
                    .productionOrderId(productionOrderId)
                    .mrpOrderNumber(event.mrpOrderNumber())
                    .warehouseId(event.warehouseId())
                    .totalComponents(0)
                    .status(MaterialRequestStatus.ERROR)
                    .errorMessage("Evento sem componentes")
                    .build();
            requestRepository.save(errRequest);
            return;
        }

        // Passo 1: Criar PMR com status PENDING
        ProductionMaterialRequest request = ProductionMaterialRequest.builder()
                .tenantId(tenantId)
                .productionOrderId(productionOrderId)
                .mrpOrderNumber(event.mrpOrderNumber())
                .warehouseId(event.warehouseId())
                .totalComponents(event.components().size())
                .status(MaterialRequestStatus.PENDING)
                .build();
        request = requestRepository.save(request);

        // Passo 2: Criar itens
        List<ProductionMaterialRequestItem> items = new ArrayList<>();
        for (MrpProductionOrderReleasedEvent.ProductionComponent comp : event.components()) {
            ProductionMaterialRequestItem item = ProductionMaterialRequestItem.builder()
                    .tenantId(tenantId)
                    .requestId(request.getId())
                    .productId(comp.productId())
                    .quantityRequested(comp.quantityRequired())
                    .build();
            items.add(itemRepository.save(item));
        }

        // Status → RESERVING
        request.setStatus(MaterialRequestStatus.RESERVING);
        request = requestRepository.save(request);

        // Passo 3: Reservar estoque por componente (FIFO)
        for (ProductionMaterialRequestItem item : items) {
            List<StockItem> available = stockItemRepository
                    .findAvailableByTenantProductWarehouse(tenantId, item.getProductId(), request.getWarehouseId());
            available.sort(Comparator.comparing(StockItem::getReceivedAt));

            int totalAvailable = available.stream().mapToInt(StockItem::getQuantityAvailable).sum();

            if (totalAvailable >= item.getQuantityRequested()) {
                // Reserva completa — FIFO por múltiplos StockItems
                int remaining = item.getQuantityRequested();
                UUID firstLocationId = null;
                UUID firstLotId = null;
                for (StockItem si : available) {
                    if (remaining <= 0) break;
                    if (firstLocationId == null) {
                        firstLocationId = si.getLocation().getId();
                        firstLotId = si.getLot() != null ? si.getLot().getId() : null;
                    }
                    int toReserve = Math.min(remaining, si.getQuantityAvailable());
                    si.setQuantityAvailable(si.getQuantityAvailable() - toReserve);
                    si.setQuantityReserved(si.getQuantityReserved() + toReserve);
                    stockItemRepository.save(si);
                    remaining -= toReserve;
                }
                item.setQuantityReserved(item.getQuantityRequested());
                item.setLocationId(firstLocationId);
                item.setLotId(firstLotId);
                item.setShortage(false);
            } else {
                // Shortage — reserva o que há
                int totalReserved = 0;
                UUID firstLocationId = null;
                UUID firstLotId = null;
                for (StockItem si : available) {
                    if (si.getQuantityAvailable() <= 0) continue;
                    if (firstLocationId == null) {
                        firstLocationId = si.getLocation().getId();
                        firstLotId = si.getLot() != null ? si.getLot().getId() : null;
                    }
                    int toReserve = si.getQuantityAvailable();
                    si.setQuantityReserved(si.getQuantityReserved() + toReserve);
                    si.setQuantityAvailable(0);
                    stockItemRepository.save(si);
                    totalReserved += toReserve;
                }
                item.setQuantityReserved(totalReserved);
                item.setLocationId(firstLocationId);
                item.setLotId(firstLotId);
                item.setShortage(true);
                request.setShortageComponents(request.getShortageComponents() + 1);
            }
            itemRepository.save(item);
        }

        if (request.getShortageComponents() == 0) {
            // Passo 4A: Criar PickingOrder PRODUCTION_ORDER
            try {
                createPickingOrderForRequest(request, items, tenantId, productionOrderId);
            } catch (Exception e) {
                log.error("Saga compensation: falha ao criar PickingOrder para OP {}: {}",
                        productionOrderId, e.getMessage(), e);
                compensateReservations(items, tenantId);
                request.setStatus(MaterialRequestStatus.ERROR);
                request.setErrorMessage(e.getMessage());
                requestRepository.save(request);
                return;
            }
        } else {
            // Passo 4B: Shortage
            request.setStatus(MaterialRequestStatus.STOCK_SHORTAGE);
            request = requestRepository.save(request);
            publishShortageEvent(event, request, items, tenantId);
        }

        // AuditLog (AC12)
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PRODUCTION_MATERIAL_REQUEST")
                .entityId(request.getId())
                .action(AuditAction.CREATE)
                .actorId(SYSTEM_ACTOR_ID)
                .newValue(String.format(
                        "{\"productionOrderId\":\"%s\",\"status\":\"%s\",\"totalComponents\":%d}",
                        productionOrderId, request.getStatus(), request.getTotalComponents()))
                .build();
        AuditLog savedLog = auditLogRepository.save(auditLog);
        auditLogIndexer.indexAuditLogAsync(savedLog);

        log.info("ProductionMaterialRequest {} criada para OP {} (status={} tenant={})",
                request.getId(), productionOrderId, request.getStatus(), tenantId);
    }

    // =========================================================================
    // AC5 — processar MrpProductionOrderCancelledEvent
    // =========================================================================

    @Transactional
    public void processProductionOrderCancelled(MrpProductionOrderCancelledEvent event) {
        UUID tenantId = event.tenantId();
        UUID productionOrderId = event.productionOrderId();

        Optional<ProductionMaterialRequest> requestOpt =
                requestRepository.findByTenantIdAndProductionOrderId(tenantId, productionOrderId);

        if (requestOpt.isEmpty()) {
            log.warn("PMR não encontrada para productionOrderId={} no cancelamento — evento ignorado", productionOrderId);
            return;
        }

        ProductionMaterialRequest request = requestOpt.get();

        if (request.getStatus() == MaterialRequestStatus.DELIVERED
                || request.getStatus() == MaterialRequestStatus.FINISHED_GOODS_RECEIVED) {
            log.info("OP {} já concluída (status={}), cancelamento ignorado", productionOrderId, request.getStatus());
            return;
        }

        if (request.getStatus() == MaterialRequestStatus.PICKING_IN_PROGRESS) {
            log.warn("OP {} cancelada enquanto picking em andamento (pickingOrder={}). Intervenção manual necessária.",
                    productionOrderId, request.getPickingOrderId());
            request.setStatus(MaterialRequestStatus.CANCELLED);
            request.setCancellationReason(event.cancellationReason());
            request.setCancelledAt(Instant.now());
            requestRepository.save(request);
            recordCancelAudit(request, tenantId, true);
            return;
        }

        // Liberar reservas
        List<ProductionMaterialRequestItem> items =
                itemRepository.findByTenantIdAndRequestId(tenantId, request.getId());
        for (ProductionMaterialRequestItem item : items) {
            if (item.getQuantityReserved() <= 0 || item.getLocationId() == null) continue;
            releaseItemReservation(item, tenantId);
        }

        // Cancelar PickingOrder se PICKING_PENDING
        if (request.getPickingOrderId() != null
                && request.getStatus() == MaterialRequestStatus.PICKING_PENDING) {
            pickingOrderRepository.findById(request.getPickingOrderId()).ifPresent(po -> {
                po.setStatus(PickingStatus.CANCELLED);
                po.setCancelledAt(Instant.now());
                po.setCancellationReason("OP cancelada pelo MRP: " + event.cancellationReason());
                pickingOrderRepository.save(po);
            });
        }

        request.setStatus(MaterialRequestStatus.CANCELLED);
        request.setCancellationReason(event.cancellationReason());
        request.setCancelledAt(Instant.now());
        requestRepository.save(request);
        recordCancelAudit(request, tenantId, false);
    }

    // =========================================================================
    // AC6 — confirmDelivery: PICKING_PENDING/PICKING_IN_PROGRESS → DELIVERED
    // =========================================================================

    @Transactional
    public ProductionMaterialRequestResponse confirmDelivery(UUID requestId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ProductionMaterialRequest request = getRequestForTenant(requestId, tenantId);

        if (request.getStatus() != MaterialRequestStatus.PICKING_PENDING
                && request.getStatus() != MaterialRequestStatus.PICKING_IN_PROGRESS) {
            throw new ConflictException("Status inválido para confirmação de entrega: " + request.getStatus());
        }

        UUID actorId = extractOperatorId();
        List<ProductionMaterialRequestItem> items =
                itemRepository.findByTenantIdAndRequestId(tenantId, requestId);

        for (ProductionMaterialRequestItem item : items) {
            if (item.getQuantityReserved() <= 0) continue;

            ProductWms product = productWmsRepository.findByIdAndTenantId(item.getProductId(), tenantId)
                    .orElse(null);
            if (product == null) {
                log.warn("Produto {} não encontrado ao criar StockMovement OUTBOUND", item.getProductId());
                continue;
            }

            Lot lot = item.getLotId() != null ? lotRepository.findById(item.getLotId()).orElse(null) : null;
            Location sourceLocation = item.getLocationId() != null
                    ? locationRepository.findById(item.getLocationId()).orElse(null)
                    : null;

            // StockMovement OUTBOUND
            StockMovement movement = StockMovement.builder()
                    .tenantId(tenantId)
                    .type(MovementType.OUTBOUND)
                    .product(product)
                    .lot(lot)
                    .sourceLocation(sourceLocation)
                    .quantity(item.getQuantityReserved())
                    .referenceType(ReferenceType.PRODUCTION_ORDER)
                    .referenceId(requestId)
                    .operatorId(actorId)
                    .build();
            stockMovementRepository.save(movement);

            // Liberar quantityReserved (o stock foi fisicamente entregue)
            if (item.getLocationId() != null) {
                releaseItemReservation(item, tenantId);
            }

            item.setQuantityDelivered(item.getQuantityReserved());
            itemRepository.save(item);
        }

        request.setStatus(MaterialRequestStatus.DELIVERED);
        request.setConfirmedDeliveryAt(Instant.now());
        request.setConfirmedBy(actorId);
        ProductionMaterialRequest saved = requestRepository.save(request);

        // Publicar evento
        List<MaterialsDeliveredEvent.DeliveredItem> deliveredItems = items.stream()
                .filter(i -> i.getQuantityDelivered() > 0)
                .map(i -> new MaterialsDeliveredEvent.DeliveredItem(i.getProductId(), i.getLotId(), i.getQuantityDelivered()))
                .collect(Collectors.toList());

        materialsDeliveredEventPublisher.publishMaterialsDelivered(new MaterialsDeliveredEvent(
                "WMS_MATERIALS_DELIVERED",
                tenantId,
                saved.getProductionOrderId(),
                requestId,
                saved.getWarehouseId(),
                deliveredItems,
                actorId,
                saved.getConfirmedDeliveryAt()
        ));

        // AuditLog (AC12)
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PRODUCTION_MATERIAL_REQUEST")
                .entityId(requestId)
                .action(AuditAction.MOVEMENT)
                .actorId(actorId)
                .newValue(String.format(
                        "{\"requestId\":\"%s\",\"status\":\"DELIVERED\",\"confirmedBy\":\"%s\"}",
                        requestId, actorId))
                .build();
        AuditLog savedLog = auditLogRepository.save(auditLog);
        auditLogIndexer.indexAuditLogAsync(savedLog);

        List<ProductionMaterialRequestItemResponse> itemResponses = items.stream()
                .map(ProductionMaterialRequestItemResponse::from)
                .collect(Collectors.toList());
        return ProductionMaterialRequestResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC7 — receiveFinishedGoods: DELIVERED → FINISHED_GOODS_RECEIVED
    // =========================================================================

    @Transactional
    public ProductionMaterialRequestResponse receiveFinishedGoods(UUID requestId,
                                                                   ReceiveFinishedGoodsRequest body) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ProductionMaterialRequest request = getRequestForTenant(requestId, tenantId);

        if (request.getStatus() != MaterialRequestStatus.DELIVERED) {
            throw new ConflictException("Status inválido para recebimento de produto acabado: " + request.getStatus());
        }

        UUID actorId = extractOperatorId();
        List<FinishedGoodsReceivedEvent.ReceivedItem> receivedItems = new ArrayList<>();

        for (ReceiveFinishedGoodsRequest.FinishedGoodsItem itemReq : body.items()) {
            Location location = locationRepository.findById(itemReq.locationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location não encontrada: " + itemReq.locationId()));
            if (!tenantId.equals(location.getTenantId())) {
                throw new AccessDeniedException("Location não pertence ao tenant: " + itemReq.locationId());
            }

            ProductWms product = productWmsRepository.findByIdAndTenantId(itemReq.productId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemReq.productId()));

            Lot lot = itemReq.lotId() != null ? lotRepository.findById(itemReq.lotId()).orElse(null) : null;

            // StockMovement INBOUND
            StockMovement movement = StockMovement.builder()
                    .tenantId(tenantId)
                    .type(MovementType.INBOUND)
                    .product(product)
                    .lot(lot)
                    .destinationLocation(location)
                    .quantity(itemReq.quantityReceived())
                    .referenceType(ReferenceType.PRODUCTION_ORDER)
                    .referenceId(requestId)
                    .operatorId(actorId)
                    .build();
            stockMovementRepository.save(movement);

            // Incrementar ou criar StockItem
            UUID locationId = itemReq.locationId();
            UUID productId = itemReq.productId();
            UUID lotId = itemReq.lotId();

            Optional<StockItem> siOpt = lotId != null
                    ? stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotId(
                            tenantId, locationId, productId, lotId)
                    : stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                            tenantId, locationId, productId);

            StockItem stockItem = siOpt.orElseGet(() -> StockItem.builder()
                    .tenantId(tenantId)
                    .location(location)
                    .product(product)
                    .lot(lot)
                    .build());
            stockItem.setQuantityAvailable(stockItem.getQuantityAvailable() + itemReq.quantityReceived());
            stockItemRepository.save(stockItem);

            receivedItems.add(new FinishedGoodsReceivedEvent.ReceivedItem(
                    productId, locationId, itemReq.quantityReceived()));
        }

        request.setStatus(MaterialRequestStatus.FINISHED_GOODS_RECEIVED);
        request.setFinishedGoodsReceivedAt(Instant.now());
        ProductionMaterialRequest saved = requestRepository.save(request);

        // Publicar evento
        finishedGoodsReceivedEventPublisher.publishFinishedGoodsReceived(new FinishedGoodsReceivedEvent(
                "WMS_FINISHED_GOODS_RECEIVED",
                tenantId,
                saved.getProductionOrderId(),
                requestId,
                saved.getWarehouseId(),
                receivedItems,
                actorId,
                saved.getFinishedGoodsReceivedAt()
        ));

        // AuditLog (AC12)
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PRODUCTION_MATERIAL_REQUEST")
                .entityId(requestId)
                .action(AuditAction.MOVEMENT)
                .actorId(actorId)
                .newValue(String.format(
                        "{\"requestId\":\"%s\",\"status\":\"FINISHED_GOODS_RECEIVED\",\"receivedBy\":\"%s\"}",
                        requestId, actorId))
                .build();
        AuditLog savedLog = auditLogRepository.save(auditLog);
        auditLogIndexer.indexAuditLogAsync(savedLog);

        List<ProductionMaterialRequestItem> items =
                itemRepository.findByTenantIdAndRequestId(tenantId, requestId);
        List<ProductionMaterialRequestItemResponse> itemResponses = items.stream()
                .map(ProductionMaterialRequestItemResponse::from)
                .collect(Collectors.toList());
        return ProductionMaterialRequestResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC8 — getRequest: busca por ID com isolamento de tenant
    // =========================================================================

    @Transactional(readOnly = true)
    public ProductionMaterialRequestResponse getRequest(UUID requestId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        ProductionMaterialRequest request = getRequestForTenant(requestId, tenantId);
        List<ProductionMaterialRequestItem> items =
                itemRepository.findByTenantIdAndRequestId(tenantId, requestId);
        List<ProductionMaterialRequestItemResponse> itemResponses = items.stream()
                .map(ProductionMaterialRequestItemResponse::from)
                .collect(Collectors.toList());
        return ProductionMaterialRequestResponse.from(request, itemResponses);
    }

    // =========================================================================
    // AC9 — listRequests: listagem paginada com filtro de status
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<ProductionMaterialRequestResponse> listRequests(MaterialRequestStatus status, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Page<ProductionMaterialRequest> page;
        if (status != null) {
            page = requestRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            page = requestRepository.findByTenantId(tenantId, pageable);
        }
        // Listagem não inclui itens (AC9)
        return page.map(r -> ProductionMaterialRequestResponse.from(r, null));
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    private void createPickingOrderForRequest(ProductionMaterialRequest request,
                                               List<ProductionMaterialRequestItem> items,
                                               UUID tenantId, UUID productionOrderId) {
        PickingOrder picking = PickingOrder.builder()
                .tenantId(tenantId)
                .warehouseId(request.getWarehouseId())
                .orderType(PickingOrderType.PRODUCTION_ORDER)
                .productionOrderId(productionOrderId)
                .status(PickingStatus.PENDING)
                .createdBy(SYSTEM_ACTOR_ID)
                .build();
        PickingOrder savedPicking = pickingOrderRepository.save(picking);

        int sortOrder = 0;
        for (ProductionMaterialRequestItem item : items) {
            if (item.getLocationId() == null || item.getQuantityReserved() <= 0) continue;
            PickingItem pickingItem = PickingItem.builder()
                    .tenantId(tenantId)
                    .pickingOrderId(savedPicking.getId())
                    .productId(item.getProductId())
                    .locationId(item.getLocationId())
                    .lotId(item.getLotId())
                    .quantityRequested(item.getQuantityReserved())
                    .status(PickingItemStatus.PENDING)
                    .sortOrder(sortOrder++)
                    .build();
            pickingItemRepository.save(pickingItem);
        }

        request.setPickingOrderId(savedPicking.getId());
        request.setStatus(MaterialRequestStatus.PICKING_PENDING);
        requestRepository.save(request);
    }

    private void compensateReservations(List<ProductionMaterialRequestItem> items, UUID tenantId) {
        for (ProductionMaterialRequestItem item : items) {
            if (item.getQuantityReserved() <= 0 || item.getLocationId() == null) continue;
            releaseItemReservation(item, tenantId);
        }
    }

    private void releaseItemReservation(ProductionMaterialRequestItem item, UUID tenantId) {
        Optional<StockItem> siOpt = item.getLotId() != null
                ? stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotId(
                        tenantId, item.getLocationId(), item.getProductId(), item.getLotId())
                : stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                        tenantId, item.getLocationId(), item.getProductId());
        siOpt.ifPresent(si -> {
            si.setQuantityReserved(Math.max(0, si.getQuantityReserved() - item.getQuantityReserved()));
            si.setQuantityAvailable(si.getQuantityAvailable() + item.getQuantityReserved());
            stockItemRepository.save(si);
        });
    }

    private void publishShortageEvent(MrpProductionOrderReleasedEvent event,
                                       ProductionMaterialRequest request,
                                       List<ProductionMaterialRequestItem> items,
                                       UUID tenantId) {
        List<StockShortageEvent.ShortageItem> shortages = items.stream()
                .filter(ProductionMaterialRequestItem::isShortage)
                .map(i -> {
                    int available = stockItemRepository
                            .findAvailableByTenantProductWarehouse(tenantId, i.getProductId(), request.getWarehouseId())
                            .stream().mapToInt(StockItem::getQuantityAvailable).sum();
                    return new StockShortageEvent.ShortageItem(i.getProductId(), i.getQuantityRequested(), available);
                })
                .collect(Collectors.toList());

        stockShortageEventPublisher.publishStockShortage(new StockShortageEvent(
                "WMS_STOCK_SHORTAGE",
                tenantId,
                event.productionOrderId(),
                request.getId(),
                shortages,
                Instant.now()
        ));
    }

    private void recordCancelAudit(ProductionMaterialRequest request, UUID tenantId, boolean pickingInProgress) {
        String newValue = String.format(
                "{\"requestId\":\"%s\",\"status\":\"CANCELLED\",\"pickingInProgress\":%b,\"reason\":\"%s\"}",
                request.getId(), pickingInProgress,
                request.getCancellationReason() != null ? request.getCancellationReason() : "");
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PRODUCTION_MATERIAL_REQUEST")
                .entityId(request.getId())
                .action(AuditAction.UPDATE)
                .actorId(SYSTEM_ACTOR_ID)
                .newValue(newValue)
                .build();
        AuditLog savedLog = auditLogRepository.save(auditLog);
        auditLogIndexer.indexAuditLogAsync(savedLog);
    }

    private ProductionMaterialRequest getRequestForTenant(UUID requestId, UUID tenantId) {
        return requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> {
                    if (requestRepository.existsById(requestId)) {
                        return new AccessDeniedException("Acesso negado ao request: " + requestId);
                    }
                    return new ResourceNotFoundException("ProductionMaterialRequest não encontrado: " + requestId);
                });
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
        return SYSTEM_ACTOR_ID;
    }
}
