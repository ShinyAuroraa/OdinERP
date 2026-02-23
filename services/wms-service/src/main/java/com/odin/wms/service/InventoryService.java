package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.InventoryCount.CountType;
import com.odin.wms.domain.entity.InventoryCount.InventoryCountStatus;
import com.odin.wms.domain.entity.InventoryCountItem.ItemCountStatus;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.CreateInventoryCountRequest;
import com.odin.wms.dto.request.SecondCountRequest;
import com.odin.wms.dto.request.SubmitCountRequest;
import com.odin.wms.dto.response.*;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service de inventário físico (contagem de estoque).
 * Gerencia o ciclo completo: DRAFT → IN_PROGRESS → RECONCILED → APPROVED → CLOSED.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class InventoryService {

    static final UUID SYSTEM_OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final InventoryCountRepository inventoryCountRepository;
    private final InventoryCountItemRepository inventoryCountItemRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockBalanceService stockBalanceService;
    private final TraceabilityIndexer traceabilityIndexer;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogIndexer auditLogIndexer;

    // -------------------------------------------------------------------------
    // AC1 — criar sessão de inventário
    // -------------------------------------------------------------------------

    public InventoryCountResponse createCount(CreateInventoryCountRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        if (request.countType() == CountType.CYCLIC && request.zoneId() == null) {
            throw new BusinessException("Inventário CYCLIC requer zoneId.");
        }

        InventoryCount count = inventoryCountRepository.save(InventoryCount.builder()
                .tenantId(tenantId)
                .countType(request.countType())
                .warehouseId(request.warehouseId())
                .zoneId(request.zoneId())
                .status(InventoryCountStatus.DRAFT)
                .adjustmentThreshold(request.adjustmentThreshold())
                .createdBy(extractOperatorId())
                .build());

        List<StockItem> stockItems = request.countType() == CountType.CYCLIC
                ? stockItemRepository.findAllByTenantIdAndZoneId(tenantId, request.zoneId())
                : stockItemRepository.findByFilters(tenantId, null, null, null, request.warehouseId());

        List<InventoryCountItem> items = stockItems.stream()
                .map(si -> buildCountItem(tenantId, count.getId(), si))
                .toList();

        inventoryCountItemRepository.saveAll(items);

        log.debug("Inventário {} criado com {} itens (tenant={})", count.getId(), items.size(), tenantId);

        return toCountResponse(count, items.size());
    }

    // -------------------------------------------------------------------------
    // AC2 — iniciar contagem
    // -------------------------------------------------------------------------

    public InventoryCountResponse startCount(UUID countId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        InventoryCount count = getCount(countId, tenantId);

        if (count.getStatus() != InventoryCountStatus.DRAFT) {
            throw new BusinessException("Inventário deve estar em DRAFT para iniciar. Status: " + count.getStatus());
        }

        count.setStatus(InventoryCountStatus.IN_PROGRESS);
        count.setStartedAt(Instant.now());
        inventoryCountRepository.save(count);

        long totalItems = inventoryCountItemRepository.countByInventoryCountId(countId);
        return toCountResponse(count, totalItems);
    }

    // -------------------------------------------------------------------------
    // AC3 — lista de contagem paginada
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<InventoryCountItemResponse> getCountItems(UUID countId, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();
        getCount(countId, tenantId); // valida existência e tenant

        return inventoryCountItemRepository
                .findByInventoryCountIdWithDetails(countId, pageable)
                .map(this::toItemResponse);
    }

    // -------------------------------------------------------------------------
    // AC4 — submeter contagem de item
    // -------------------------------------------------------------------------

    public InventoryCountItemResponse submitCount(UUID countId, UUID itemId, SubmitCountRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        InventoryCount count = getCount(countId, tenantId);

        if (count.getStatus() != InventoryCountStatus.IN_PROGRESS) {
            throw new BusinessException("Inventário deve estar IN_PROGRESS para submeter contagem. Status: " + count.getStatus());
        }

        InventoryCountItem item = getItem(itemId, countId);

        item.setCountedQty(request.countedQty());
        item.setDivergenceQty(request.countedQty().subtract(item.getExpectedQty()));
        item.setDivergencePct(calculateDivergencePct(item.getExpectedQty(), request.countedQty()));
        item.setStatus(ItemCountStatus.COUNTED);

        return toItemResponse(inventoryCountItemRepository.save(item));
    }

    // -------------------------------------------------------------------------
    // AC6 — segunda contagem
    // -------------------------------------------------------------------------

    public InventoryCountItemResponse submitSecondCount(UUID countId, UUID itemId, SecondCountRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        InventoryCount count = getCount(countId, tenantId);

        if (count.getStatus() != InventoryCountStatus.RECONCILED) {
            throw new BusinessException("Segunda contagem só é permitida após reconciliação. Status: " + count.getStatus());
        }

        InventoryCountItem item = getItem(itemId, countId);

        if (item.getStatus() != ItemCountStatus.PENDING_APPROVAL) {
            throw new BusinessException("Segunda contagem só é permitida em itens PENDING_APPROVAL. Status: " + item.getStatus());
        }

        item.setSecondCountedQty(request.secondCountedQty());
        // adjustedQty = média das duas contagens
        BigDecimal avg = item.getCountedQty().add(request.secondCountedQty())
                .divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
        item.setAdjustedQty(avg);
        item.setDivergenceQty(avg.subtract(item.getExpectedQty()));
        item.setDivergencePct(calculateDivergencePct(item.getExpectedQty(), avg));
        item.setStatus(ItemCountStatus.SECOND_COUNTED);

        return toItemResponse(inventoryCountItemRepository.save(item));
    }

    // -------------------------------------------------------------------------
    // AC5 — reconciliação
    // -------------------------------------------------------------------------

    public ReconciliationSummaryResponse reconcile(UUID countId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        InventoryCount count = getCount(countId, tenantId);

        if (count.getStatus() != InventoryCountStatus.IN_PROGRESS) {
            throw new BusinessException("Reconciliação requer status IN_PROGRESS. Status: " + count.getStatus());
        }

        List<InventoryCountItem> items = inventoryCountItemRepository
                .findByInventoryCountIdAndStatusIn(countId,
                        List.of(ItemCountStatus.COUNTED, ItemCountStatus.SECOND_COUNTED));

        long autoApproved = 0;
        long pendingApproval = 0;
        long noVariance = 0;

        for (InventoryCountItem item : items) {
            BigDecimal pct = item.getDivergencePct() != null
                    ? item.getDivergencePct().abs()
                    : BigDecimal.ZERO;

            if (item.getDivergenceQty() == null || item.getDivergenceQty().compareTo(BigDecimal.ZERO) == 0) {
                item.setStatus(ItemCountStatus.AUTO_APPROVED);
                noVariance++;
                autoApproved++;
            } else if (pct.compareTo(BigDecimal.valueOf(count.getAdjustmentThreshold())) <= 0) {
                item.setStatus(ItemCountStatus.AUTO_APPROVED);
                autoApproved++;
            } else {
                item.setStatus(ItemCountStatus.PENDING_APPROVAL);
                pendingApproval++;
            }
        }

        inventoryCountItemRepository.saveAll(items);

        count.setStatus(InventoryCountStatus.RECONCILED);
        inventoryCountRepository.save(count);

        long totalItems = inventoryCountItemRepository.countByInventoryCountId(countId);
        return new ReconciliationSummaryResponse(
                totalItems, autoApproved, pendingApproval, noVariance,
                InventoryCountStatus.RECONCILED);
    }

    // -------------------------------------------------------------------------
    // AC7 — aprovação de ajustes
    // -------------------------------------------------------------------------

    public ApprovalSummaryResponse approveAdjustments(UUID countId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        InventoryCount count = getCount(countId, tenantId);

        if (count.getStatus() != InventoryCountStatus.RECONCILED) {
            throw new BusinessException("Aprovação requer status RECONCILED. Status: " + count.getStatus());
        }

        UUID operatorId = extractOperatorId();

        List<InventoryCountItem> approvableItems = inventoryCountItemRepository
                .findByInventoryCountIdAndStatusIn(countId,
                        List.of(ItemCountStatus.AUTO_APPROVED, ItemCountStatus.PENDING_APPROVAL,
                                ItemCountStatus.SECOND_COUNTED));

        int totalAdjusted = 0;
        BigDecimal totalDivergence = BigDecimal.ZERO;

        for (InventoryCountItem item : approvableItems) {
            BigDecimal effectiveQty = item.getAdjustedQty() != null
                    ? item.getAdjustedQty()
                    : item.getCountedQty();

            if (effectiveQty == null) {
                continue; // item não contado, pular
            }

            BigDecimal delta = effectiveQty.subtract(item.getExpectedQty());

            // OBS-2: divergência zero → não cria StockMovement
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                applyStockAdjustment(item, effectiveQty, delta, tenantId, operatorId, count);
                totalDivergence = totalDivergence.add(delta.abs());
                totalAdjusted++;
            }

            item.setAdjustedQty(effectiveQty);
            item.setStatus(ItemCountStatus.ADJUSTED);
        }

        inventoryCountItemRepository.saveAll(approvableItems);

        count.setStatus(InventoryCountStatus.APPROVED);
        count.setApprovedBy(operatorId);
        inventoryCountRepository.save(count);

        // Evict cache Redis para todos os saldos afetados
        stockBalanceService.evictAll();

        log.debug("Inventário {} aprovado: {} ajustes aplicados (tenant={})",
                countId, totalAdjusted, tenantId);

        return new ApprovalSummaryResponse(countId, totalAdjusted, totalDivergence, "APPROVED");
    }

    // -------------------------------------------------------------------------
    // AC8 — fechar inventário
    // -------------------------------------------------------------------------

    public InventoryCountResponse closeCount(UUID countId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        InventoryCount count = getCount(countId, tenantId);

        if (count.getStatus() != InventoryCountStatus.APPROVED) {
            throw new BusinessException("Fechamento requer status APPROVED. Status: " + count.getStatus());
        }

        count.setStatus(InventoryCountStatus.CLOSED);
        count.setClosedAt(Instant.now());
        inventoryCountRepository.save(count);

        long totalItems = inventoryCountItemRepository.countByInventoryCountId(countId);
        return toCountResponse(count, totalItems);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void applyStockAdjustment(InventoryCountItem item, BigDecimal effectiveQty,
                                      BigDecimal delta, UUID tenantId, UUID operatorId,
                                      InventoryCount count) {
        // Find StockItem for update (optimistic locking will throw on conflict → HTTP 409)
        StockItem stockItem = item.getLotId() != null
                ? stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotId(
                        tenantId, item.getLocationId(), item.getProductId(), item.getLotId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "StockItem não encontrado para item de inventário: " + item.getId()))
                : stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                        tenantId, item.getLocationId(), item.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "StockItem não encontrado para item de inventário: " + item.getId()));

        stockItem.setQuantityAvailable(effectiveQty.intValue());
        stockItemRepository.save(stockItem);

        // Create StockMovement
        StockMovement movement = stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .type(MovementType.INVENTORY_ADJUSTMENT)
                .product(stockItem.getProduct())
                .lot(stockItem.getLot())
                .destinationLocation(stockItem.getLocation())
                .quantity(delta.intValue())
                .referenceType(ReferenceType.INVENTORY_COUNT)
                .referenceId(count.getId())
                .referenceNumber("Ajuste de inventário #" + count.getId())
                .operatorId(operatorId)
                .reason("Ajuste de inventário #" + count.getId())
                .build());

        traceabilityIndexer.indexMovementAsync(movement);

        // AC5 — registrar auditoria do ajuste de inventário (AC4.4)
        UUID actorId = operatorId != null ? operatorId : SYSTEM_OPERATOR_ID;
        String newValue = String.format(
                "{\"movementType\":\"INVENTORY_ADJUSTMENT\",\"quantity\":%d,\"inventoryCountId\":\"%s\"}",
                movement.getQuantity(), count.getId());

        AuditLog auditLog = auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("STOCK_ITEM")
                .entityId(stockItem.getId())
                .action(AuditAction.MOVEMENT)
                .actorId(actorId)
                .newValue(newValue)
                .build());

        auditLogIndexer.indexAuditLogAsync(auditLog);
    }

    private InventoryCountItem buildCountItem(UUID tenantId, UUID countId, StockItem si) {
        return InventoryCountItem.builder()
                .tenantId(tenantId)
                .inventoryCountId(countId)
                .locationId(si.getLocation().getId())
                .productId(si.getProduct().getId())
                .lotId(si.getLot() != null ? si.getLot().getId() : null)
                .expectedQty(BigDecimal.valueOf(si.getQuantityAvailable()))
                .status(ItemCountStatus.PENDING)
                .build();
    }

    private InventoryCount getCount(UUID countId, UUID tenantId) {
        return inventoryCountRepository.findByIdAndTenantId(countId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventário não encontrado: " + countId));
    }

    private InventoryCountItem getItem(UUID itemId, UUID countId) {
        return inventoryCountItemRepository.findByIdAndInventoryCountId(itemId, countId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item de inventário não encontrado: " + itemId));
    }

    private BigDecimal calculateDivergencePct(BigDecimal expected, BigDecimal counted) {
        if (expected == null || expected.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return counted.subtract(expected)
                .abs()
                .divide(expected, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private InventoryCountResponse toCountResponse(InventoryCount count, long totalItems) {
        return new InventoryCountResponse(
                count.getId(),
                count.getCountType(),
                count.getWarehouseId(),
                count.getZoneId(),
                count.getStatus(),
                count.getAdjustmentThreshold(),
                totalItems,
                count.getStartedAt(),
                count.getClosedAt(),
                count.getCreatedAt()
        );
    }

    private InventoryCountItemResponse toItemResponse(InventoryCountItem item) {
        String locationCode = item.getLocation() != null ? item.getLocation().getCode() : null;
        String productCode = item.getProduct() != null ? item.getProduct().getSku() : null;
        String lotNumber = item.getLot() != null ? item.getLot().getLotNumber() : null;
        java.time.LocalDate expiryDate = (item.getLot() != null && item.getLot().getExpiryDate() != null)
                ? item.getLot().getExpiryDate()
                : null;

        return new InventoryCountItemResponse(
                item.getId(),
                locationCode,
                productCode,
                lotNumber,
                expiryDate,
                item.getExpectedQty(),
                item.getCountedQty(),
                item.getDivergenceQty(),
                item.getDivergencePct(),
                item.getStatus()
        );
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
}
