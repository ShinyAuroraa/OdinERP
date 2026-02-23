package com.odin.wms.inventory;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.InventoryCount.CountType;
import com.odin.wms.domain.entity.InventoryCount.InventoryCountStatus;
import com.odin.wms.domain.entity.InventoryCountItem.ItemCountStatus;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.CreateInventoryCountRequest;
import com.odin.wms.dto.request.SecondCountRequest;
import com.odin.wms.dto.request.SubmitCountRequest;
import com.odin.wms.dto.response.*;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityIndexer;
import com.odin.wms.service.InventoryService;
import com.odin.wms.service.StockBalanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para InventoryService — AC11 (8 cenários).
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryCountRepository inventoryCountRepository;
    @Mock private InventoryCountItemRepository inventoryCountItemRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private StockBalanceService stockBalanceService;
    @Mock private TraceabilityIndexer traceabilityIndexer;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogIndexer auditLogIndexer;

    @InjectMocks
    private InventoryService inventoryService;

    private static final UUID TENANT_ID    = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID ZONE_ID      = UUID.randomUUID();
    private static final UUID COUNT_ID     = UUID.randomUUID();
    private static final UUID ITEM_ID      = UUID.randomUUID();
    private static final UUID LOCATION_ID  = UUID.randomUUID();
    private static final UUID PRODUCT_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // createCount — AC11 cenários 1-2
    // -------------------------------------------------------------------------

    @Test
    void createCount_fullType_generatesAllStockItems() {
        StockItem si = buildStockItem(100);
        when(stockItemRepository.findByFilters(TENANT_ID, null, null, null, WAREHOUSE_ID))
                .thenReturn(List.of(si));
        when(inventoryCountRepository.save(any())).thenAnswer(inv -> {
            InventoryCount c = inv.getArgument(0);
            setUUID(c, COUNT_ID);
            return c;
        });
        when(inventoryCountItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryCountResponse resp = inventoryService.createCount(
                new CreateInventoryCountRequest(CountType.FULL, WAREHOUSE_ID, null, 0));

        assertThat(resp.countType()).isEqualTo(CountType.FULL);
        assertThat(resp.status()).isEqualTo(InventoryCountStatus.DRAFT);
        assertThat(resp.totalItems()).isEqualTo(1);
        verify(inventoryCountItemRepository).saveAll(argThat(list ->
                ((List<?>) list).size() == 1));
    }

    @Test
    void createCount_cyclicType_generatesItemsInZone() {
        StockItem si = buildStockItem(50);
        when(stockItemRepository.findAllByTenantIdAndZoneId(TENANT_ID, ZONE_ID))
                .thenReturn(List.of(si));
        when(inventoryCountRepository.save(any())).thenAnswer(inv -> {
            InventoryCount c = inv.getArgument(0);
            setUUID(c, COUNT_ID);
            return c;
        });
        when(inventoryCountItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryCountResponse resp = inventoryService.createCount(
                new CreateInventoryCountRequest(CountType.CYCLIC, WAREHOUSE_ID, ZONE_ID, 5));

        assertThat(resp.countType()).isEqualTo(CountType.CYCLIC);
        assertThat(resp.zoneId()).isEqualTo(ZONE_ID);
        assertThat(resp.totalItems()).isEqualTo(1);
        verify(stockItemRepository).findAllByTenantIdAndZoneId(TENANT_ID, ZONE_ID);
        verify(stockItemRepository, never()).findByFilters(any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // reconcile — AC11 cenários 3-4
    // -------------------------------------------------------------------------

    @Test
    void reconcile_itemWithinThreshold_autoApproves() {
        InventoryCount count = buildCount(InventoryCountStatus.IN_PROGRESS, 5); // threshold 5%
        InventoryCountItem item = buildItem(ItemCountStatus.COUNTED, BigDecimal.valueOf(100), BigDecimal.valueOf(97));
        // divergencePct = 3% ≤ 5% → AUTO_APPROVED

        when(inventoryCountRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(count));
        when(inventoryCountItemRepository.findByInventoryCountIdAndStatusIn(eq(COUNT_ID), anyList()))
                .thenReturn(List.of(item));
        when(inventoryCountItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountRepository.save(any())).thenReturn(count);
        when(inventoryCountItemRepository.countByInventoryCountId(COUNT_ID)).thenReturn(1L);

        ReconciliationSummaryResponse resp = inventoryService.reconcile(COUNT_ID);

        assertThat(resp.autoApproved()).isEqualTo(1);
        assertThat(resp.pendingApproval()).isZero();
        assertThat(resp.status()).isEqualTo(InventoryCountStatus.RECONCILED);
        assertThat(item.getStatus()).isEqualTo(ItemCountStatus.AUTO_APPROVED);
    }

    @Test
    void reconcile_itemAboveThreshold_pendingApproval() {
        InventoryCount count = buildCount(InventoryCountStatus.IN_PROGRESS, 5); // threshold 5%
        InventoryCountItem item = buildItem(ItemCountStatus.COUNTED, BigDecimal.valueOf(100), BigDecimal.valueOf(85));
        // divergencePct = 15% > 5% → PENDING_APPROVAL

        when(inventoryCountRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(count));
        when(inventoryCountItemRepository.findByInventoryCountIdAndStatusIn(eq(COUNT_ID), anyList()))
                .thenReturn(List.of(item));
        when(inventoryCountItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountRepository.save(any())).thenReturn(count);
        when(inventoryCountItemRepository.countByInventoryCountId(COUNT_ID)).thenReturn(1L);

        ReconciliationSummaryResponse resp = inventoryService.reconcile(COUNT_ID);

        assertThat(resp.pendingApproval()).isEqualTo(1);
        assertThat(resp.autoApproved()).isZero();
        assertThat(item.getStatus()).isEqualTo(ItemCountStatus.PENDING_APPROVAL);
    }

    // -------------------------------------------------------------------------
    // approve — AC11 cenários 5-7
    // -------------------------------------------------------------------------

    @Test
    void approve_createsStockMovementsAndEvictsCache() {
        InventoryCount count = buildCount(InventoryCountStatus.RECONCILED, 0);
        InventoryCountItem item = buildItem(ItemCountStatus.PENDING_APPROVAL,
                BigDecimal.valueOf(100), BigDecimal.valueOf(90));
        StockItem stockItem = buildStockItem(100);

        when(inventoryCountRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(count));
        when(inventoryCountItemRepository.findByInventoryCountIdAndStatusIn(eq(COUNT_ID), anyList()))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID)).thenReturn(Optional.of(stockItem));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountRepository.save(any())).thenReturn(count);

        ApprovalSummaryResponse resp = inventoryService.approveAdjustments(COUNT_ID);

        assertThat(resp.totalAdjusted()).isEqualTo(1);
        assertThat(resp.status()).isEqualTo("APPROVED");

        ArgumentCaptor<StockMovement> movCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movCaptor.capture());
        assertThat(movCaptor.getValue().getType()).isEqualTo(MovementType.INVENTORY_ADJUSTMENT);
        assertThat(movCaptor.getValue().getQuantity()).isEqualTo(-10); // 90 - 100

        verify(stockBalanceService).evictAll();
    }

    @Test
    void approve_callsTraceabilityIndexer() {
        InventoryCount count = buildCount(InventoryCountStatus.RECONCILED, 0);
        InventoryCountItem item = buildItem(ItemCountStatus.AUTO_APPROVED,
                BigDecimal.valueOf(50), BigDecimal.valueOf(45));
        StockItem stockItem = buildStockItem(50);

        when(inventoryCountRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(count));
        when(inventoryCountItemRepository.findByInventoryCountIdAndStatusIn(eq(COUNT_ID), anyList()))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID)).thenReturn(Optional.of(stockItem));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountRepository.save(any())).thenReturn(count);

        inventoryService.approveAdjustments(COUNT_ID);

        verify(traceabilityIndexer).indexMovementAsync(any(StockMovement.class));
    }

    @Test
    void approve_withOptimisticLock_throwsException() {
        InventoryCount count = buildCount(InventoryCountStatus.RECONCILED, 0);
        InventoryCountItem item = buildItem(ItemCountStatus.PENDING_APPROVAL,
                BigDecimal.valueOf(100), BigDecimal.valueOf(80));
        StockItem stockItem = buildStockItem(100);

        when(inventoryCountRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(count));
        when(inventoryCountItemRepository.findByInventoryCountIdAndStatusIn(eq(COUNT_ID), anyList()))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID)).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.save(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(StockItem.class, stockItem.getId()));

        assertThatThrownBy(() -> inventoryService.approveAdjustments(COUNT_ID))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // -------------------------------------------------------------------------
    // secondCount — AC11 cenário 8
    // -------------------------------------------------------------------------

    @Test
    void secondCount_recalculatesDivergence() {
        InventoryCount count = buildCount(InventoryCountStatus.RECONCILED, 5);
        // First count: expected=100, counted=80 → divergence 20%
        InventoryCountItem item = buildItem(ItemCountStatus.PENDING_APPROVAL,
                BigDecimal.valueOf(100), BigDecimal.valueOf(80));

        when(inventoryCountRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(count));
        when(inventoryCountItemRepository.findByIdAndInventoryCountId(ITEM_ID, COUNT_ID))
                .thenReturn(Optional.of(item));
        when(inventoryCountItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryCountItemResponse resp = inventoryService.submitSecondCount(COUNT_ID, ITEM_ID,
                new SecondCountRequest(BigDecimal.valueOf(84)));

        // adjustedQty = média(80, 84) = 82
        assertThat(resp.status()).isEqualTo(ItemCountStatus.SECOND_COUNTED);
        // item.adjustedQty should be 82
        assertThat(item.getAdjustedQty()).isEqualByComparingTo(BigDecimal.valueOf(82));
        // divergencePct = abs(82-100)/100 * 100 = 18%
        assertThat(item.getDivergencePct()).isEqualByComparingTo(new BigDecimal("18.0000"));
    }

    // -------------------------------------------------------------------------
    // U9 — applyStockAdjustment_savesAuditLog (AC5 Story 4.4)
    // -------------------------------------------------------------------------

    @Test
    void approve_savesAuditLogWithMovementAction() {
        InventoryCount count = buildCount(InventoryCountStatus.RECONCILED, 0);
        InventoryCountItem item = buildItem(ItemCountStatus.AUTO_APPROVED,
                BigDecimal.valueOf(100), BigDecimal.valueOf(90));
        StockItem stockItem = buildStockItem(100);

        when(inventoryCountRepository.findByIdAndTenantId(COUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(count));
        when(inventoryCountItemRepository.findByInventoryCountIdAndStatusIn(eq(COUNT_ID), anyList()))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID)).thenReturn(Optional.of(stockItem));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryCountRepository.save(any())).thenReturn(count);

        inventoryService.approveAdjustments(COUNT_ID);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());

        AuditLog captured = auditCaptor.getValue();
        assertThat(captured.getAction()).isEqualTo(AuditAction.MOVEMENT);
        assertThat(captured.getEntityType()).isEqualTo("STOCK_ITEM");
        assertThat(captured.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captured.getNewValue()).contains("INVENTORY_ADJUSTMENT");

        verify(auditLogIndexer).indexAuditLogAsync(any(AuditLog.class));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private InventoryCount buildCount(InventoryCountStatus status, int threshold) {
        InventoryCount count = InventoryCount.builder()
                .tenantId(TENANT_ID)
                .countType(CountType.FULL)
                .warehouseId(WAREHOUSE_ID)
                .status(status)
                .adjustmentThreshold(threshold)
                .build();
        setUUID(count, COUNT_ID);
        return count;
    }

    private InventoryCountItem buildItem(ItemCountStatus status, BigDecimal expected, BigDecimal counted) {
        InventoryCountItem item = InventoryCountItem.builder()
                .tenantId(TENANT_ID)
                .inventoryCountId(COUNT_ID)
                .locationId(LOCATION_ID)
                .productId(PRODUCT_ID)
                .expectedQty(expected)
                .countedQty(counted)
                .divergenceQty(counted.subtract(expected))
                .divergencePct(counted.subtract(expected).abs()
                        .divide(expected, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)))
                .status(status)
                .build();
        setUUID(item, ITEM_ID);
        return item;
    }

    private StockItem buildStockItem(int qty) {
        ProductWms product = ProductWms.builder()
                .tenantId(TENANT_ID)
                .sku("SKU-001")
                .name("Produto Teste")
                .storageType(com.odin.wms.domain.enums.StorageType.DRY)
                .controlsLot(false).controlsSerial(false).controlsExpiry(false).active(true)
                .build();
        setUUIDBase(product, PRODUCT_ID);

        Location location = Location.builder()
                .tenantId(TENANT_ID)
                .code("A-01-01")
                .type(com.odin.wms.domain.enums.LocationType.STORAGE)
                .build();
        setUUIDBase(location, LOCATION_ID);

        StockItem item = StockItem.builder()
                .tenantId(TENANT_ID)
                .location(location)
                .product(product)
                .quantityAvailable(qty)
                .build();
        setUUIDBase(item, UUID.randomUUID());
        return item;
    }

    private void setUUID(Object entity, UUID id) {
        try {
            Class<?> base = Class.forName("com.odin.wms.domain.entity.base.BaseEntity");
            var field = base.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao definir UUID via reflexão", e);
        }
    }

    private void setUUIDBase(Object entity, UUID id) {
        setUUID(entity, id);
    }
}
