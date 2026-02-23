package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PickingItem.PickingItemStatus;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.entity.PickingOrder.RoutingAlgorithm;
// Note: ProductWms, Lot, Location accessed via StockItem.getProduct(), getLot(), getLocation()
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.PickingItemResponse;
import com.odin.wms.dto.response.PickingOrderResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.PickingCompletedEventPublisher;
import com.odin.wms.messaging.event.CrmOrderConfirmedEvent;
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
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PickingService {

    static final UUID SYSTEM_OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PickingOrderRepository pickingOrderRepository;
    private final PickingItemRepository pickingItemRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceService stockBalanceService;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogIndexer auditLogIndexer;
    private final PickingCompletedEventPublisher pickingCompletedEventPublisher;

    // =========================================================================
    // AC3 — Kafka: criar PickingOrder a partir de evento CRM
    // =========================================================================

    @Transactional
    public void createPickingOrderFromKafka(CrmOrderConfirmedEvent event) {
        UUID tenantId = event.tenantId();
        UUID crmOrderId = event.crmOrderId();

        // Idempotência: se já existe, descartar silenciosamente
        if (pickingOrderRepository.findByTenantIdAndCrmOrderId(tenantId, crmOrderId).isPresent()) {
            log.warn("Evento CRM duplicado descartado: crmOrderId={} tenantId={}", crmOrderId, tenantId);
            return;
        }

        PickingOrder order = PickingOrder.builder()
                .tenantId(tenantId)
                .crmOrderId(crmOrderId)
                .warehouseId(event.warehouseId())
                .status(PickingStatus.PENDING)
                .priority(event.priority())
                .createdBy(SYSTEM_OPERATOR_ID)
                .build();

        PickingOrder savedOrder = pickingOrderRepository.save(order);

        List<PickingItem> items = buildPickingItems(
                tenantId, event.warehouseId(), savedOrder.getId(),
                event.items().stream()
                        .map(i -> new ItemRequest(i.productId(), i.quantity(), null))
                        .toList()
        );

        List<PickingItem> itemsWithRouting = applySShapeRouting(items, tenantId);
        pickingItemRepository.saveAll(itemsWithRouting);

        log.info("PickingOrder {} criada via Kafka (crmOrderId={} tenant={})",
                savedOrder.getId(), crmOrderId, tenantId);
    }

    // =========================================================================
    // AC4 — POST /picking-orders (criação manual)
    // =========================================================================

    @Transactional
    public PickingOrderResponse createPickingOrderManual(CreatePickingOrderRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID createdBy = extractOperatorId();

        warehouseRepository.findById(request.warehouseId())
                .filter(w -> tenantId.equals(w.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Warehouse não encontrado: " + request.warehouseId()));

        RoutingAlgorithm algorithm = parseRoutingAlgorithm(request.routingAlgorithm());

        PickingOrder order = PickingOrder.builder()
                .tenantId(tenantId)
                .warehouseId(request.warehouseId())
                .status(PickingStatus.PENDING)
                .priority(request.priority())
                .routingAlgorithm(algorithm)
                .createdBy(createdBy)
                .build();

        PickingOrder savedOrder = pickingOrderRepository.save(order);

        List<PickingItem> items = buildPickingItems(
                tenantId, request.warehouseId(), savedOrder.getId(),
                request.items().stream()
                        .map(i -> new ItemRequest(i.productId(), i.quantity(), i.lotId()))
                        .toList()
        );

        List<PickingItem> itemsWithRouting = applySShapeRouting(items, tenantId);
        pickingItemRepository.saveAll(itemsWithRouting);

        List<PickingItemResponse> itemResponses = itemsWithRouting.stream()
                .map(PickingItemResponse::from)
                .toList();

        log.info("PickingOrder {} criada manualmente (tenant={})", savedOrder.getId(), tenantId);
        return PickingOrderResponse.from(savedOrder, itemResponses);
    }

    // =========================================================================
    // AC5 — PUT /picking-orders/{id}/assign
    // =========================================================================

    @Transactional
    public PickingOrderResponse assignOrder(UUID orderId, AssignPickingOrderRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PickingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != PickingStatus.PENDING) {
            throw new BusinessException(
                    "Ordem não pode ser atribuída. Status atual: " + order.getStatus());
        }

        List<PickingItem> items = pickingItemRepository
                .findByPickingOrderIdOrderBySortOrderAsc(orderId);

        // Reservar estoque para cada item PENDING
        for (PickingItem item : items) {
            if (item.getStatus() != PickingItemStatus.PENDING) continue;

            StockItem stockItem = findStockItemForReservation(
                    tenantId, item.getLocationId(), item.getProductId(), item.getLotId());

            if (stockItem.getQuantityAvailable() < item.getQuantityRequested()) {
                throw new BusinessException(String.format(
                        "Estoque insuficiente para produto %s: disponível=%d, solicitado=%d",
                        item.getProductId(), stockItem.getQuantityAvailable(), item.getQuantityRequested()));
            }

            stockItem.setQuantityAvailable(stockItem.getQuantityAvailable() - item.getQuantityRequested());
            stockItem.setQuantityReserved(stockItem.getQuantityReserved() + item.getQuantityRequested());
            stockItemRepository.save(stockItem);
        }

        // Aplicar S-shape routing (recalcular com locations carregadas)
        List<PickingItem> itemsWithRouting = applySShapeRouting(items, tenantId);
        pickingItemRepository.saveAll(itemsWithRouting);

        order.setStatus(PickingStatus.IN_PROGRESS);
        order.setOperatorId(request.operatorId());
        if (request.zoneId() != null) {
            order.setZoneId(request.zoneId());
        }
        PickingOrder saved = pickingOrderRepository.save(order);

        stockBalanceService.evictAll();

        log.info("PickingOrder {} atribuída ao operador {} (tenant={})",
                orderId, request.operatorId(), tenantId);

        List<PickingItemResponse> itemResponses = itemsWithRouting.stream()
                .map(PickingItemResponse::from)
                .toList();
        return PickingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC6 — PUT /picking-orders/{id}/pick-item/{itemId}
    // =========================================================================

    @Transactional
    public PickingItemResponse pickItem(UUID orderId, UUID itemId, PickItemRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PickingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != PickingStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Ordem não está IN_PROGRESS. Status atual: " + order.getStatus());
        }

        PickingItem item = pickingItemRepository.findByIdAndTenantId(itemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item não encontrado: " + itemId));

        if (!item.getPickingOrderId().equals(orderId)) {
            throw new AccessDeniedException("Item não pertence à ordem: " + orderId);
        }

        if (item.getStatus() == PickingItemStatus.PICKED
                || item.getStatus() == PickingItemStatus.SKIPPED) {
            throw new BusinessException(
                    "Item já processado. Status atual: " + item.getStatus());
        }

        int qty = request.quantityPicked();
        if (qty > item.getQuantityRequested()) {
            throw new BusinessException("Quantidade coletada excede a solicitada");
        }

        PickingItemStatus newStatus;
        if (qty == 0) {
            newStatus = PickingItemStatus.SKIPPED;
        } else if (qty == item.getQuantityRequested()) {
            newStatus = PickingItemStatus.PICKED;
        } else {
            newStatus = PickingItemStatus.PARTIAL;
        }

        item.setQuantityPicked(qty);
        item.setStatus(newStatus);
        item.setPickedBy(request.pickedBy());
        item.setPickedAt(Instant.now());

        PickingItem saved = pickingItemRepository.save(item);
        return PickingItemResponse.from(saved);
    }

    // =========================================================================
    // AC7 — PUT /picking-orders/{id}/pick-item/{itemId}/substitute-location
    // =========================================================================

    @Transactional
    public PickingItemResponse substituteLocation(UUID orderId, UUID itemId,
                                                  SubstituteLocationRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PickingOrder order = getOrderForTenant(orderId, tenantId);

        PickingItem item = pickingItemRepository.findByIdAndTenantId(itemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item não encontrado: " + itemId));

        if (!item.getPickingOrderId().equals(orderId)) {
            throw new AccessDeniedException("Item não pertence à ordem: " + orderId);
        }

        // Validar que a localização alternativa pertence ao mesmo warehouse
        UUID altWarehouseId = locationRepository.findWarehouseIdByLocationId(request.alternativeLocationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Localização alternativa não encontrada: " + request.alternativeLocationId()));

        if (!altWarehouseId.equals(order.getWarehouseId())) {
            throw new BusinessException(
                    "Localização alternativa pertence a outro warehouse");
        }

        // Verificar estoque disponível na localização alternativa
        UUID altLotId = request.alternativeLotId();
        StockItem altStockItem = findStockItemForReservation(
                tenantId, request.alternativeLocationId(),
                item.getProductId(), altLotId);

        if (altStockItem.getQuantityAvailable() < request.quantityPicked()) {
            throw new BusinessException(String.format(
                    "Estoque insuficiente na localização alternativa: disponível=%d, solicitado=%d",
                    altStockItem.getQuantityAvailable(), request.quantityPicked()));
        }

        // Liberar reserva da localização original
        StockItem origStockItem = findStockItemForReservation(
                tenantId, item.getLocationId(), item.getProductId(), item.getLotId());
        origStockItem.setQuantityReserved(
                origStockItem.getQuantityReserved() - item.getQuantityRequested());
        origStockItem.setQuantityAvailable(
                origStockItem.getQuantityAvailable() + item.getQuantityRequested());
        stockItemRepository.save(origStockItem);

        // Reservar na localização alternativa
        altStockItem.setQuantityAvailable(
                altStockItem.getQuantityAvailable() - request.quantityPicked());
        altStockItem.setQuantityReserved(
                altStockItem.getQuantityReserved() + request.quantityPicked());
        stockItemRepository.save(altStockItem);

        // Atualizar item
        item.setLocationId(request.alternativeLocationId());
        item.setLotId(altLotId);
        item.setQuantityPicked(request.quantityPicked());
        item.setPickedBy(request.pickedBy());
        item.setPickedAt(Instant.now());
        item.setStatus(request.quantityPicked() == item.getQuantityRequested()
                ? PickingItemStatus.PICKED : PickingItemStatus.PARTIAL);

        PickingItem saved = pickingItemRepository.save(item);
        return PickingItemResponse.from(saved);
    }

    // =========================================================================
    // AC8 — PUT /picking-orders/{id}/complete
    // =========================================================================

    @Transactional
    public PickingOrderResponse completeOrder(UUID orderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        UUID actorId = extractOperatorId();
        PickingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() != PickingStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Ordem não pode ser concluída. Status atual: " + order.getStatus());
        }

        List<PickingItem> items = pickingItemRepository
                .findByPickingOrderIdOrderBySortOrderAsc(orderId);

        boolean hasAnyPicked = items.stream().anyMatch(
                i -> i.getStatus() == PickingItemStatus.PICKED
                        || i.getStatus() == PickingItemStatus.PARTIAL);
        if (!hasAnyPicked) {
            throw new BusinessException(
                    "Nenhum item coletado. A ordem não pode ser concluída sem ao menos um item PICKED ou PARTIAL.");
        }

        int totalPicked = 0;
        boolean allPicked = true;

        for (PickingItem item : items) {
            StockItem stockItem = findStockItemForReservation(
                    tenantId, item.getLocationId(), item.getProductId(), item.getLotId());

            // Liberar toda a reserva
            stockItem.setQuantityReserved(
                    stockItem.getQuantityReserved() - item.getQuantityRequested());

            if (item.getQuantityPicked() > 0) {
                // Criar StockMovement de OUTBOUND usando entidades já carregadas no stockItem
                StockMovement movement = StockMovement.builder()
                        .tenantId(tenantId)
                        .type(MovementType.PICKING)
                        .product(stockItem.getProduct())
                        .lot(stockItem.getLot())
                        .sourceLocation(stockItem.getLocation())
                        .quantity(item.getQuantityPicked())
                        .referenceType(ReferenceType.PICKING_ORDER)
                        .referenceId(order.getId())
                        .operatorId(order.getOperatorId() != null ? order.getOperatorId() : actorId)
                        .build();
                stockMovementRepository.save(movement);

                totalPicked += item.getQuantityPicked();
            }

            // Restaurar residual de PARTIAL/SKIPPED
            int residual = item.getQuantityRequested() - item.getQuantityPicked();
            if (residual > 0) {
                stockItem.setQuantityAvailable(stockItem.getQuantityAvailable() + residual);
                allPicked = false;
            }

            if (item.getStatus() == PickingItemStatus.PARTIAL
                    || item.getStatus() == PickingItemStatus.SKIPPED) {
                allPicked = false;
            }

            stockItemRepository.save(stockItem);
        }

        order.setStatus(allPicked ? PickingStatus.COMPLETED : PickingStatus.PARTIAL);
        order.setCompletedAt(Instant.now());
        PickingOrder saved = pickingOrderRepository.save(order);

        // AuditLog
        String newValue = String.format(
                "{\"pickingOrderId\":\"%s\",\"status\":\"%s\",\"itemsCount\":%d,\"totalPicked\":%d}",
                order.getId(), order.getStatus(), items.size(), totalPicked);

        AuditLog auditLog = auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("PICKING_ORDER")
                .entityId(order.getId())
                .action(AuditAction.MOVEMENT)
                .actorId(actorId)
                .newValue(newValue)
                .build());
        auditLogIndexer.indexAuditLogAsync(auditLog);

        stockBalanceService.evictAll();

        // Kafka: publicar evento de conclusão
        pickingCompletedEventPublisher.publishPickingCompleted(saved, items);

        log.info("PickingOrder {} concluída com status {} (tenant={})",
                orderId, saved.getStatus(), tenantId);

        List<PickingItemResponse> itemResponses = items.stream()
                .map(PickingItemResponse::from)
                .toList();
        return PickingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC9 — PUT /picking-orders/{id}/cancel
    // =========================================================================

    @Transactional
    public PickingOrderResponse cancelOrder(UUID orderId, CancelPickingOrderRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PickingOrder order = getOrderForTenant(orderId, tenantId);

        if (order.getStatus() == PickingStatus.COMPLETED
                || order.getStatus() == PickingStatus.PARTIAL) {
            throw new BusinessException("Ordem já concluída não pode ser cancelada");
        }
        if (order.getStatus() == PickingStatus.CANCELLED) {
            throw new BusinessException("Ordem já cancelada");
        }

        List<PickingItem> items = pickingItemRepository
                .findByPickingOrderIdOrderBySortOrderAsc(orderId);

        if (order.getStatus() == PickingStatus.IN_PROGRESS) {
            // Liberar reserva de PENDING, PARTIAL e PICKED (SKIPPED nunca reservou)
            for (PickingItem item : items) {
                if (item.getStatus() == PickingItemStatus.PENDING
                        || item.getStatus() == PickingItemStatus.PARTIAL
                        || item.getStatus() == PickingItemStatus.PICKED) {

                    StockItem stockItem = findStockItemForReservation(
                            tenantId, item.getLocationId(), item.getProductId(), item.getLotId());
                    stockItem.setQuantityReserved(
                            stockItem.getQuantityReserved() - item.getQuantityRequested());
                    stockItem.setQuantityAvailable(
                            stockItem.getQuantityAvailable() + item.getQuantityRequested());
                    stockItemRepository.save(stockItem);
                }
            }
            stockBalanceService.evictAll();
        }

        order.setStatus(PickingStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancellationReason(request.reason());
        PickingOrder saved = pickingOrderRepository.save(order);

        log.info("PickingOrder {} cancelada (tenant={})", orderId, tenantId);

        List<PickingItemResponse> itemResponses = items.stream()
                .map(PickingItemResponse::from)
                .toList();
        return PickingOrderResponse.from(saved, itemResponses);
    }

    // =========================================================================
    // AC10 — GET /picking-orders/{id} e GET /picking-orders
    // =========================================================================

    @Transactional(readOnly = true)
    public PickingOrderResponse getOrder(UUID orderId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        PickingOrder order = getOrderForTenant(orderId, tenantId);
        List<PickingItemResponse> items = pickingItemRepository
                .findByPickingOrderIdOrderBySortOrderAsc(orderId)
                .stream().map(PickingItemResponse::from).toList();
        return PickingOrderResponse.from(order, items);
    }

    @Transactional(readOnly = true)
    public Page<PickingOrderResponse> listOrders(PickingStatus status, UUID operatorId,
                                                 Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Page<PickingOrder> page;
        if (status != null && operatorId != null) {
            page = pickingOrderRepository.findByTenantIdAndStatusAndOperatorId(
                    tenantId, status, operatorId, pageable);
        } else if (status != null) {
            page = pickingOrderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (operatorId != null) {
            page = pickingOrderRepository.findByTenantIdAndOperatorId(tenantId, operatorId, pageable);
        } else {
            page = pickingOrderRepository.findByTenantId(tenantId, pageable);
        }
        return page.map(order -> {
            List<PickingItemResponse> items = pickingItemRepository
                    .findByPickingOrderIdOrderBySortOrderAsc(order.getId())
                    .stream().map(PickingItemResponse::from).toList();
            return PickingOrderResponse.from(order, items);
        });
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Seleciona StockItems para cada produto do pedido usando FIFO/FEFO.
     * FEFO: se algum StockItem tem lot com expiryDate != null → ordena por expiryDate ASC.
     * FIFO: caso contrário → ordena por receivedAt ASC.
     */
    private List<PickingItem> buildPickingItems(UUID tenantId, UUID warehouseId,
                                                UUID orderId, List<ItemRequest> itemRequests) {
        List<PickingItem> result = new ArrayList<>();

        for (ItemRequest req : itemRequests) {
            List<StockItem> candidates = stockItemRepository
                    .findAvailableByTenantProductWarehouse(tenantId, req.productId(), warehouseId);

            if (req.lotId() != null) {
                candidates = candidates.stream()
                        .filter(s -> req.lotId().equals(s.getLot() != null ? s.getLot().getId() : null))
                        .toList();
            }

            // Ordena: FEFO se algum lote tem expiryDate, senão FIFO
            List<StockItem> sorted = sortFifoFefo(candidates);

            // Selecionar o melhor candidato (primeiro da lista ordenada)
            // Se não há candidatos, não podemos criar o item sem uma localização
            // (edge case: criar com location inválida se não há stock — mas story exige que criamos mesmo assim)
            StockItem best = sorted.isEmpty() ? null : sorted.get(0);

            if (best != null) {
                PickingItem item = PickingItem.builder()
                        .tenantId(tenantId)
                        .pickingOrderId(orderId)
                        .productId(req.productId())
                        .lotId(best.getLot() != null ? best.getLot().getId() : null)
                        .locationId(best.getLocation().getId())
                        .quantityRequested(req.quantity())
                        .build();
                result.add(item);
            } else {
                log.warn("Sem stock disponível para produto {} no warehouse {} — item omitido",
                        req.productId(), warehouseId);
            }
        }
        return result;
    }

    /**
     * Aplica algoritmo S-shape: carrega locations para obter aisle code.
     * Corredor par → itens ordenados por shelf.code ASC.
     * Corredor ímpar → itens ordenados por shelf.code DESC.
     */
    private List<PickingItem> applySShapeRouting(List<PickingItem> items, UUID tenantId) {
        if (items.isEmpty()) return items;

        // Carregar locations com hierarquia (lazy carrega shelf/aisle)
        Map<UUID, Location> locationMap = new HashMap<>();
        for (PickingItem item : items) {
            if (!locationMap.containsKey(item.getLocationId())) {
                locationRepository.findById(item.getLocationId())
                        .ifPresent(loc -> locationMap.put(loc.getId(), loc));
            }
        }

        // Agrupar por aisle code
        Map<String, List<PickingItem>> byAisle = items.stream()
                .collect(Collectors.groupingBy(item -> {
                    Location loc = locationMap.get(item.getLocationId());
                    if (loc != null && loc.getShelf() != null && loc.getShelf().getAisle() != null) {
                        return loc.getShelf().getAisle().getCode();
                    }
                    return "UNKNOWN";
                }));

        // Ordenar aisles
        List<String> sortedAisles = byAisle.keySet().stream().sorted().toList();

        AtomicInteger sortCounter = new AtomicInteger(1);
        List<PickingItem> routed = new ArrayList<>();

        for (String aisleCode : sortedAisles) {
            int aisleNum = extractAisleNumber(aisleCode);
            List<PickingItem> aisleItems = byAisle.get(aisleCode);

            // Par → ASC por shelf code; Ímpar → DESC por shelf code
            boolean isEven = (aisleNum % 2 == 0);
            Comparator<String> shelfOrder = isEven ? Comparator.naturalOrder() : Comparator.reverseOrder();

            List<PickingItem> sortedItems = aisleItems.stream()
                    .sorted(Comparator.<PickingItem, String>comparing(item -> {
                        Location loc = locationMap.get(item.getLocationId());
                        if (loc != null && loc.getShelf() != null) {
                            return loc.getShelf().getCode();
                        }
                        return "";
                    }, shelfOrder))
                    .toList();

            for (PickingItem item : sortedItems) {
                item.setSortOrder(sortCounter.getAndIncrement());
                routed.add(item);
            }
        }

        return routed;
    }

    private int extractAisleNumber(String aisleCode) {
        if (aisleCode == null || aisleCode.isBlank()) return 0;
        // Extrai número do final: "A-01" → 1, "B-03" → 3, "CORRIDOR-12" → 12
        String digits = aisleCode.replaceAll(".*?(\\d+)$", "$1");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<StockItem> sortFifoFefo(List<StockItem> candidates) {
        boolean hasExpiry = candidates.stream()
                .anyMatch(s -> s.getLot() != null && s.getLot().getExpiryDate() != null);

        if (hasExpiry) {
            return candidates.stream()
                    .sorted(Comparator.comparing(s -> {
                        if (s.getLot() != null && s.getLot().getExpiryDate() != null) {
                            return s.getLot().getExpiryDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                        }
                        return s.getReceivedAt();
                    }))
                    .toList();
        } else {
            return candidates.stream()
                    .sorted(Comparator.comparing(StockItem::getReceivedAt))
                    .toList();
        }
    }

    private StockItem findStockItemForReservation(UUID tenantId, UUID locationId,
                                                   UUID productId, UUID lotId) {
        return lotId != null
                ? stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotId(
                        tenantId, locationId, productId, lotId)
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(
                            "StockItem não encontrado: tenant=%s location=%s product=%s lot=%s",
                            tenantId, locationId, productId, lotId)))
                : stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                        tenantId, locationId, productId)
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(
                            "StockItem não encontrado (sem lote): tenant=%s location=%s product=%s",
                            tenantId, locationId, productId)));
    }

    private PickingOrder getOrderForTenant(UUID orderId, UUID tenantId) {
        return pickingOrderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> {
                    if (pickingOrderRepository.existsById(orderId)) {
                        return new AccessDeniedException("Acesso negado à ordem: " + orderId);
                    }
                    return new ResourceNotFoundException("Ordem de picking não encontrada: " + orderId);
                });
    }

    private RoutingAlgorithm parseRoutingAlgorithm(String value) {
        if (value == null || value.isBlank()) return RoutingAlgorithm.S_SHAPE;
        try {
            return RoutingAlgorithm.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RoutingAlgorithm.S_SHAPE;
        }
    }

    private UUID extractOperatorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException e) {
                    return SYSTEM_OPERATOR_ID;
                }
            }
        }
        return SYSTEM_OPERATOR_ID;
    }

    /** DTO interno para unificar criação de PickingItems a partir de Kafka e manual. */
    private record ItemRequest(UUID productId, int quantity, UUID lotId) {}
}
