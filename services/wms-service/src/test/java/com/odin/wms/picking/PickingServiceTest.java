package com.odin.wms.picking;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PickingItem.PickingItemStatus;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.PickingItemResponse;
import com.odin.wms.dto.response.PickingOrderResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.PickingCompletedEventPublisher;
import com.odin.wms.messaging.event.CrmOrderConfirmedEvent;
import com.odin.wms.service.PickingService;
import com.odin.wms.service.StockBalanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para PickingService — U1-U18.
 */
@ExtendWith(MockitoExtension.class)
class PickingServiceTest {

    @Mock private PickingOrderRepository pickingOrderRepository;
    @Mock private PickingItemRepository pickingItemRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private StockBalanceService stockBalanceService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogIndexer auditLogIndexer;
    @Mock private PickingCompletedEventPublisher pickingCompletedEventPublisher;

    @InjectMocks
    private PickingService pickingService;

    private static final UUID TENANT_ID      = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID   = UUID.randomUUID();
    private static final UUID PRODUCT_ID     = UUID.randomUUID();
    private static final UUID LOCATION_ID    = UUID.randomUUID();
    private static final UUID ORDER_ID       = UUID.randomUUID();
    private static final UUID ITEM_ID        = UUID.randomUUID();
    private static final UUID OPERATOR_ID    = UUID.randomUUID();
    private static final UUID CRM_ORDER_ID   = UUID.randomUUID();

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

    // =========================================================================
    // U1 — createFromKafka_newCrmOrder_createsPendingOrder
    // =========================================================================

    @Test
    void createFromKafka_newCrmOrder_createsPendingOrder() {
        when(pickingOrderRepository.findByTenantIdAndCrmOrderId(TENANT_ID, CRM_ORDER_ID))
                .thenReturn(Optional.empty());
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });

        Location loc = buildLocation();
        StockItem si = buildStockItem(10, null);
        when(stockItemRepository.findAvailableByTenantProductWarehouse(TENANT_ID, PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(si));
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(loc));
        when(pickingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CrmOrderConfirmedEvent event = new CrmOrderConfirmedEvent(
                "SALES_ORDER_CONFIRMED", TENANT_ID, CRM_ORDER_ID, WAREHOUSE_ID, 1,
                List.of(new CrmOrderConfirmedEvent.CrmOrderItem(PRODUCT_ID, 5)));

        pickingService.createPickingOrderFromKafka(event);

        verify(pickingOrderRepository).save(argThat(o ->
                o.getStatus() == PickingStatus.PENDING
                && o.getCrmOrderId().equals(CRM_ORDER_ID)));
        verify(pickingItemRepository).saveAll(any());
    }

    // =========================================================================
    // U2 — createFromKafka_duplicateCrmOrderId_isIdempotent
    // =========================================================================

    @Test
    void createFromKafka_duplicateCrmOrderId_isIdempotent() {
        PickingOrder existing = buildOrder(PickingStatus.PENDING);
        when(pickingOrderRepository.findByTenantIdAndCrmOrderId(TENANT_ID, CRM_ORDER_ID))
                .thenReturn(Optional.of(existing));

        CrmOrderConfirmedEvent event = new CrmOrderConfirmedEvent(
                "SALES_ORDER_CONFIRMED", TENANT_ID, CRM_ORDER_ID, WAREHOUSE_ID, 1,
                List.of(new CrmOrderConfirmedEvent.CrmOrderItem(PRODUCT_ID, 5)));

        pickingService.createPickingOrderFromKafka(event);

        verify(pickingOrderRepository, never()).save(any());
        verify(pickingItemRepository, never()).saveAll(any());
    }

    // =========================================================================
    // U3 — selectStockItems_fefo_selectsEarliestExpiryDate
    // =========================================================================

    @Test
    void selectStockItems_fefo_selectsEarliestExpiryDate() {
        Lot lotNear = buildLot(LocalDate.now().plusDays(10));
        Lot lotFar  = buildLot(LocalDate.now().plusDays(100));
        StockItem nearExpiry = buildStockItem(5, lotNear);
        StockItem farExpiry  = buildStockItem(5, lotFar);
        // farExpiry retornado antes intencionalmente — FEFO deve reordenar
        when(stockItemRepository.findAvailableByTenantProductWarehouse(TENANT_ID, PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(farExpiry, nearExpiry));
        when(pickingOrderRepository.findByTenantIdAndCrmOrderId(any(), any()))
                .thenReturn(Optional.empty());
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(buildLocation()));
        when(pickingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CrmOrderConfirmedEvent event = new CrmOrderConfirmedEvent(
                "SALES_ORDER_CONFIRMED", TENANT_ID, CRM_ORDER_ID, WAREHOUSE_ID, 1,
                List.of(new CrmOrderConfirmedEvent.CrmOrderItem(PRODUCT_ID, 3)));

        pickingService.createPickingOrderFromKafka(event);

        // O item criado deve usar a localização do nearExpiry (menor expiryDate → FEFO)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PickingItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(pickingItemRepository).saveAll(captor.capture());
        List<PickingItem> savedItems = captor.getValue();
        assertThat(savedItems).hasSize(1);
        assertThat(savedItems.get(0).getLotId()).isEqualTo(nearExpiry.getLot().getId());
    }

    // =========================================================================
    // U4 — selectStockItems_fifo_selectsOldestReceivedAt
    // =========================================================================

    @Test
    void selectStockItems_fifo_selectsOldestReceivedAt() {
        StockItem older  = buildStockItemWithReceivedAt(5, null, Instant.now().minusSeconds(3600));
        StockItem newer  = buildStockItemWithReceivedAt(5, null, Instant.now());
        // newer retornado antes — FIFO deve reordenar
        when(stockItemRepository.findAvailableByTenantProductWarehouse(TENANT_ID, PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(newer, older));
        when(pickingOrderRepository.findByTenantIdAndCrmOrderId(any(), any()))
                .thenReturn(Optional.empty());
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(buildLocation()));
        when(pickingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CrmOrderConfirmedEvent event = new CrmOrderConfirmedEvent(
                "SALES_ORDER_CONFIRMED", TENANT_ID, CRM_ORDER_ID, WAREHOUSE_ID, 1,
                List.of(new CrmOrderConfirmedEvent.CrmOrderItem(PRODUCT_ID, 3)));
        pickingService.createPickingOrderFromKafka(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PickingItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(pickingItemRepository).saveAll(captor.capture());
        // Older deve ser selecionado (menor receivedAt)
        assertThat(captor.getValue().get(0).getLotId()).isNull(); // sem lote
    }

    // =========================================================================
    // U5 — selectStockItems_nullExpiryDate_fallsBackToFifo
    // =========================================================================

    @Test
    void selectStockItems_nullExpiryDate_fallsBackToFifo() {
        Lot lotNoExpiry = buildLot(null); // expiryDate = null
        StockItem si = buildStockItem(5, lotNoExpiry);
        when(stockItemRepository.findAvailableByTenantProductWarehouse(TENANT_ID, PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(si));
        when(pickingOrderRepository.findByTenantIdAndCrmOrderId(any(), any()))
                .thenReturn(Optional.empty());
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(buildLocation()));
        when(pickingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CrmOrderConfirmedEvent event = new CrmOrderConfirmedEvent(
                "SALES_ORDER_CONFIRMED", TENANT_ID, CRM_ORDER_ID, WAREHOUSE_ID, 1,
                List.of(new CrmOrderConfirmedEvent.CrmOrderItem(PRODUCT_ID, 2)));

        // Should not throw NPE
        assertThatCode(() -> pickingService.createPickingOrderFromKafka(event))
                .doesNotThrowAnyException();
    }

    // =========================================================================
    // U6 — applySShapeRouting_multipleAisles_correctSortOrder
    // =========================================================================

    @Test
    void applySShapeRouting_multipleAisles_correctSortOrder() {
        // Corredor A-01 (ímpar → DESC shelf): P03, P02, P01 → sort 1, 2, 3
        // Corredor A-02 (par  → ASC  shelf):  P01, P02 → sort 4, 5
        UUID productId2 = UUID.randomUUID();
        UUID prodId3    = UUID.randomUUID();
        UUID prodId4    = UUID.randomUUID();
        UUID prodId5    = UUID.randomUUID();

        Aisle a01 = buildAisle("A-01");
        Aisle a02 = buildAisle("A-02");
        Shelf shP01a1 = buildShelf(a01, "P01");
        Shelf shP02a1 = buildShelf(a01, "P02");
        Shelf shP03a1 = buildShelf(a01, "P03");
        Shelf shP01a2 = buildShelf(a02, "P01");
        Shelf shP02a2 = buildShelf(a02, "P02");

        Location locA1P03 = buildLocationWithShelf(UUID.randomUUID(), shP03a1);
        Location locA1P02 = buildLocationWithShelf(UUID.randomUUID(), shP02a1);
        Location locA1P01 = buildLocationWithShelf(UUID.randomUUID(), shP01a1);
        Location locA2P01 = buildLocationWithShelf(UUID.randomUUID(), shP01a2);
        Location locA2P02 = buildLocationWithShelf(UUID.randomUUID(), shP02a2);

        PickingOrder order = buildOrder(PickingStatus.PENDING);
        PickingItem item1 = buildItem(ORDER_ID, PRODUCT_ID,     locA1P03.getId(), 5);
        PickingItem item2 = buildItem(ORDER_ID, productId2,     locA1P02.getId(), 5);
        PickingItem item3 = buildItem(ORDER_ID, prodId3,        locA1P01.getId(), 5);
        PickingItem item4 = buildItem(ORDER_ID, prodId4,        locA2P01.getId(), 5);
        PickingItem item5 = buildItem(ORDER_ID, prodId5,        locA2P02.getId(), 5);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item1, item2, item3, item4, item5));
        // thenAnswer garante que cada invocação recebe um novo StockItem (evita mutação compartilhada)
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                eq(TENANT_ID), any(), any()))
                .thenAnswer(inv -> Optional.of(buildStockItem(10, null)));
        when(locationRepository.findById(locA1P03.getId())).thenReturn(Optional.of(locA1P03));
        when(locationRepository.findById(locA1P02.getId())).thenReturn(Optional.of(locA1P02));
        when(locationRepository.findById(locA1P01.getId())).thenReturn(Optional.of(locA1P01));
        when(locationRepository.findById(locA2P01.getId())).thenReturn(Optional.of(locA2P01));
        when(locationRepository.findById(locA2P02.getId())).thenReturn(Optional.of(locA2P02));
        when(pickingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickingOrderRepository.save(any())).thenReturn(order);

        pickingService.assignOrder(ORDER_ID, new AssignPickingOrderRequest(OPERATOR_ID, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PickingItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(pickingItemRepository).saveAll(captor.capture());
        List<PickingItem> routed = captor.getValue();

        // Corredor A-01 ímpar → DESC: P03 < P02 < P01 → sort 1,2,3
        // Corredor A-02 par   → ASC:  P01 < P02 → sort 4,5
        int p03Sort = routed.stream().filter(i -> i.getLocationId().equals(locA1P03.getId()))
                .findFirst().get().getSortOrder();
        int p01a1Sort = routed.stream().filter(i -> i.getLocationId().equals(locA1P01.getId()))
                .findFirst().get().getSortOrder();
        int p01a2Sort = routed.stream().filter(i -> i.getLocationId().equals(locA2P01.getId()))
                .findFirst().get().getSortOrder();
        int p02a2Sort = routed.stream().filter(i -> i.getLocationId().equals(locA2P02.getId()))
                .findFirst().get().getSortOrder();

        // P03 no corredor ímpar deve vir antes de P01 (DESC)
        assertThat(p03Sort).isLessThan(p01a1Sort);
        // P01 no corredor par deve vir antes de P02 (ASC)
        assertThat(p01a2Sort).isLessThan(p02a2Sort);
    }

    // =========================================================================
    // U7 — assignOrder_success_reservesStock
    // =========================================================================

    @Test
    void assignOrder_success_reservesStock() {
        PickingOrder order = buildOrder(PickingStatus.PENDING);
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 5);
        StockItem si = buildStockItem(10, null);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.of(si));
        when(locationRepository.findById(LOCATION_ID))
                .thenReturn(Optional.of(buildLocation()));
        when(pickingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickingOrderRepository.save(any())).thenReturn(order);

        pickingService.assignOrder(ORDER_ID, new AssignPickingOrderRequest(OPERATOR_ID, null));

        assertThat(si.getQuantityAvailable()).isEqualTo(5);   // 10 - 5
        assertThat(si.getQuantityReserved()).isEqualTo(5);    // 0 + 5
        verify(stockItemRepository).save(si);
        verify(stockBalanceService).evictAll();
    }

    // =========================================================================
    // U8 — assignOrder_insufficientStock_throwsException
    // =========================================================================

    @Test
    void assignOrder_insufficientStock_throwsException() {
        PickingOrder order = buildOrder(PickingStatus.PENDING);
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);
        StockItem si = buildStockItem(3, null); // disponível < solicitado

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.of(si));

        assertThatThrownBy(() ->
                pickingService.assignOrder(ORDER_ID, new AssignPickingOrderRequest(OPERATOR_ID, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Estoque insuficiente");
    }

    // =========================================================================
    // U9 — pickItem_fullQuantity_returnsPickedStatus
    // =========================================================================

    @Test
    void pickItem_fullQuantity_returnsPickedStatus() {
        PickingOrder order = buildOrder(PickingStatus.IN_PROGRESS);
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
                .thenReturn(Optional.of(item));
        when(pickingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PickingItemResponse resp = pickingService.pickItem(ORDER_ID, ITEM_ID,
                new PickItemRequest(10, OPERATOR_ID));

        assertThat(resp.status()).isEqualTo(PickingItemStatus.PICKED.name());
        assertThat(resp.quantityPicked()).isEqualTo(10);
    }

    // =========================================================================
    // U10 — pickItem_partialQuantity_returnsPartialStatus
    // =========================================================================

    @Test
    void pickItem_partialQuantity_returnsPartialStatus() {
        PickingOrder order = buildOrder(PickingStatus.IN_PROGRESS);
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
                .thenReturn(Optional.of(item));
        when(pickingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PickingItemResponse resp = pickingService.pickItem(ORDER_ID, ITEM_ID,
                new PickItemRequest(7, OPERATOR_ID));

        assertThat(resp.status()).isEqualTo(PickingItemStatus.PARTIAL.name());
        assertThat(resp.quantityPicked()).isEqualTo(7);
    }

    // =========================================================================
    // U11 — pickItem_zeroQuantity_returnsSkippedStatus
    // =========================================================================

    @Test
    void pickItem_zeroQuantity_returnsSkippedStatus() {
        PickingOrder order = buildOrder(PickingStatus.IN_PROGRESS);
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
                .thenReturn(Optional.of(item));
        when(pickingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PickingItemResponse resp = pickingService.pickItem(ORDER_ID, ITEM_ID,
                new PickItemRequest(0, OPERATOR_ID));

        assertThat(resp.status()).isEqualTo(PickingItemStatus.SKIPPED.name());
    }

    // =========================================================================
    // U12 — completeOrder_allPicked_returnsCompleted
    // =========================================================================

    @Test
    void completeOrder_allPicked_returnsCompleted() {
        PickingOrder order = buildOrderInProgress();
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);
        item.setQuantityPicked(10);
        item.setStatus(PickingItemStatus.PICKED);

        StockItem si = buildStockItem(0, null);
        si.setQuantityReserved(10);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.of(si));
        when(stockMovementRepository.saveAll(any())).thenReturn(List.of());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });

        PickingOrderResponse resp = pickingService.completeOrder(ORDER_ID);

        assertThat(resp.status()).isEqualTo(PickingStatus.COMPLETED.name());
        assertThat(si.getQuantityReserved()).isZero();
    }

    // =========================================================================
    // U13 — completeOrder_somePartial_returnsPartial
    // =========================================================================

    @Test
    void completeOrder_somePartial_returnsPartial() {
        PickingOrder order = buildOrderInProgress();
        PickingItem item1 = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);
        item1.setQuantityPicked(10);
        item1.setStatus(PickingItemStatus.PICKED);

        UUID prod2 = UUID.randomUUID();
        UUID loc2  = UUID.randomUUID();
        PickingItem item2 = buildItem(ORDER_ID, prod2, loc2, 5);
        item2.setQuantityPicked(3);
        item2.setStatus(PickingItemStatus.PARTIAL);

        StockItem si1 = buildStockItem(0, null); si1.setQuantityReserved(10);
        StockItem si2 = buildStockItem(0, null); si2.setQuantityReserved(5);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item1, item2));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.of(si1));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, loc2, prod2))
                .thenReturn(Optional.of(si2));
        when(stockMovementRepository.saveAll(any())).thenReturn(List.of());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });

        PickingOrderResponse resp = pickingService.completeOrder(ORDER_ID);

        assertThat(resp.status()).isEqualTo(PickingStatus.PARTIAL.name());
    }

    // =========================================================================
    // U14 — completeOrder_releasesResidualReserve
    // =========================================================================

    @Test
    void completeOrder_releasesResidualReserve() {
        PickingOrder order = buildOrderInProgress();
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);
        item.setQuantityPicked(7);
        item.setStatus(PickingItemStatus.PARTIAL);

        StockItem si = buildStockItem(0, null);
        si.setQuantityReserved(10);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.of(si));
        when(stockMovementRepository.saveAll(any())).thenReturn(List.of());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });

        pickingService.completeOrder(ORDER_ID);

        // Reserva liberada: 10 - 10 = 0
        assertThat(si.getQuantityReserved()).isZero();
        // Residual restaurado: 0 + (10 - 7) = 3
        assertThat(si.getQuantityAvailable()).isEqualTo(3);
    }

    // =========================================================================
    // U15 — cancelOrder_inProgress_releasesReservedStock
    // =========================================================================

    @Test
    void cancelOrder_inProgress_releasesReservedStock() {
        PickingOrder order = buildOrderInProgress();
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 10);
        item.setStatus(PickingItemStatus.PENDING);

        StockItem si = buildStockItem(0, null);
        si.setQuantityReserved(10);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.of(si));
        when(pickingOrderRepository.save(any())).thenReturn(order);

        pickingService.cancelOrder(ORDER_ID, new CancelPickingOrderRequest("teste"));

        assertThat(si.getQuantityReserved()).isZero();
        assertThat(si.getQuantityAvailable()).isEqualTo(10);
        verify(stockBalanceService).evictAll();
    }

    // =========================================================================
    // U16 — cancelOrder_pending_nothingToRelease
    // =========================================================================

    @Test
    void cancelOrder_pending_nothingToRelease() {
        PickingOrder order = buildOrder(PickingStatus.PENDING);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of());
        when(pickingOrderRepository.save(any())).thenReturn(order);

        pickingService.cancelOrder(ORDER_ID, new CancelPickingOrderRequest("sem reserva"));

        verify(stockItemRepository, never()).saveAll(any());
        verify(stockBalanceService, never()).evictAll();
    }

    // =========================================================================
    // U17 — cancelOrder_completed_throwsException
    // =========================================================================

    @Test
    void cancelOrder_completed_throwsException() {
        PickingOrder order = buildOrder(PickingStatus.COMPLETED);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                pickingService.cancelOrder(ORDER_ID, new CancelPickingOrderRequest("motivo")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ordem já concluída");
    }

    // =========================================================================
    // U18 — completeOrder_savesAuditLog
    // =========================================================================

    @Test
    void completeOrder_savesAuditLog() {
        PickingOrder order = buildOrderInProgress();
        PickingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOCATION_ID, 5);
        item.setQuantityPicked(5);
        item.setStatus(PickingItemStatus.PICKED);

        StockItem si = buildStockItem(0, null);
        si.setQuantityReserved(5);

        when(pickingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(pickingItemRepository.findByPickingOrderIdOrderBySortOrderAsc(ORDER_ID))
                .thenReturn(List.of(item));
        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.of(si));
        when(stockMovementRepository.saveAll(any())).thenReturn(List.of());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
            return o;
        });

        pickingService.completeOrder(ORDER_ID);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getEntityType()).isEqualTo("PICKING_ORDER");
        assertThat(log.getAction()).isEqualTo(AuditAction.MOVEMENT);
        verify(auditLogIndexer).indexAuditLogAsync(any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PickingOrder buildOrder(PickingStatus status) {
        PickingOrder o = PickingOrder.builder()
                .tenantId(TENANT_ID)
                .warehouseId(WAREHOUSE_ID)
                .status(status)
                .createdBy(OPERATOR_ID)
                .build();
        setEntityId(o, ORDER_ID, "com.odin.wms.domain.entity.base.BaseEntity");
        return o;
    }

    private PickingOrder buildOrderInProgress() {
        PickingOrder o = buildOrder(PickingStatus.IN_PROGRESS);
        o.setOperatorId(OPERATOR_ID);
        return o;
    }

    private PickingItem buildItem(UUID orderId, UUID productId, UUID locationId, int qty) {
        PickingItem item = PickingItem.builder()
                .tenantId(TENANT_ID)
                .pickingOrderId(orderId)
                .productId(productId)
                .locationId(locationId)
                .quantityRequested(qty)
                .build();
        setEntityId(item, ITEM_ID, "com.odin.wms.domain.entity.base.BaseEntity");
        return item;
    }

    private StockItem buildStockItem(int available, Lot lot) {
        Location loc = buildLocation();
        StockItem si = StockItem.builder()
                .tenantId(TENANT_ID)
                .location(loc)
                .product(buildProduct())
                .lot(lot)
                .quantityAvailable(available)
                .quantityReserved(0)
                .build();
        setEntityId(si, UUID.randomUUID(), "com.odin.wms.domain.entity.base.BaseEntity");
        return si;
    }

    private StockItem buildStockItemWithReceivedAt(int available, Lot lot, Instant receivedAt) {
        StockItem si = buildStockItem(available, lot);
        ReflectionTestUtils.setField(si, "receivedAt", receivedAt);
        return si;
    }

    private Lot buildLot(LocalDate expiryDate) {
        Lot lot = Lot.builder()
                .tenantId(TENANT_ID)
                .lotNumber("LOT-001")
                .expiryDate(expiryDate)
                .build();
        setEntityId(lot, UUID.randomUUID(), "com.odin.wms.domain.entity.base.BaseAppendOnlyEntity");
        return lot;
    }

    private Location buildLocation() {
        Aisle aisle = buildAisle("A-01");
        Shelf shelf = buildShelf(aisle, "P01");
        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .shelf(shelf)
                .code("A-01-P01")
                .fullAddress("A-01-P01")
                .build();
        setEntityId(loc, LOCATION_ID, "com.odin.wms.domain.entity.base.BaseEntity");
        return loc;
    }

    private Location buildLocationWithShelf(UUID id, Shelf shelf) {
        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .shelf(shelf)
                .code("loc-" + id)
                .fullAddress("loc-" + id)
                .build();
        setEntityId(loc, id, "com.odin.wms.domain.entity.base.BaseEntity");
        return loc;
    }

    private Aisle buildAisle(String code) {
        Aisle a = Aisle.builder()
                .tenantId(TENANT_ID)
                .code(code)
                .build();
        setEntityId(a, UUID.randomUUID(), "com.odin.wms.domain.entity.base.BaseEntity");
        return a;
    }

    private Shelf buildShelf(Aisle aisle, String code) {
        Shelf s = Shelf.builder()
                .tenantId(TENANT_ID)
                .aisle(aisle)
                .code(code)
                .build();
        setEntityId(s, UUID.randomUUID(), "com.odin.wms.domain.entity.base.BaseEntity");
        return s;
    }

    private ProductWms buildProduct() {
        ProductWms p = new ProductWms();
        setEntityId(p, PRODUCT_ID, "com.odin.wms.domain.entity.base.BaseEntity");
        return p;
    }

    private static void setEntityId(Object entity, UUID id, String baseClass) {
        try {
            Class<?> clazz = Class.forName(baseClass);
            ReflectionTestUtils.setField(entity, clazz, "id", id, UUID.class);
        } catch (ClassNotFoundException e) {
            ReflectionTestUtils.setField(entity, "id", id);
        }
    }
}
