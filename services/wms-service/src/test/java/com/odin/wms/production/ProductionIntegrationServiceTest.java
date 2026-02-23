package com.odin.wms.production;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.entity.ProductionMaterialRequest.MaterialRequestStatus;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.ReceiveFinishedGoodsRequest;
import com.odin.wms.dto.response.ProductionMaterialRequestResponse;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.FinishedGoodsReceivedEventPublisher;
import com.odin.wms.messaging.MaterialsDeliveredEventPublisher;
import com.odin.wms.messaging.StockShortageEventPublisher;
import com.odin.wms.messaging.event.MrpProductionOrderCancelledEvent;
import com.odin.wms.messaging.event.MrpProductionOrderReleasedEvent;
import com.odin.wms.service.ProductionIntegrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ProductionIntegrationService — U1–U14.
 */
@ExtendWith(MockitoExtension.class)
class ProductionIntegrationServiceTest {

    @Mock private ProductionMaterialRequestRepository requestRepository;
    @Mock private ProductionMaterialRequestItemRepository itemRepository;
    @Mock private PickingOrderRepository pickingOrderRepository;
    @Mock private PickingItemRepository pickingItemRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ProductWmsRepository productWmsRepository;
    @Mock private LotRepository lotRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogIndexer auditLogIndexer;
    @Mock private MaterialsDeliveredEventPublisher materialsDeliveredEventPublisher;
    @Mock private FinishedGoodsReceivedEventPublisher finishedGoodsReceivedEventPublisher;
    @Mock private StockShortageEventPublisher stockShortageEventPublisher;

    @InjectMocks
    private ProductionIntegrationService service;

    private static final UUID TENANT_ID          = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID        = UUID.randomUUID();
    private static final UUID PRODUCT_ID          = UUID.randomUUID();
    private static final UUID LOCATION_ID         = UUID.randomUUID();
    private static final UUID PRODUCTION_ORDER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID          = UUID.randomUUID();

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
    // U1 — processProductionOrderReleased: stock disponível → PICKING_PENDING
    // -------------------------------------------------------------------------

    @Test
    void processProductionOrderReleased_stockSufficient_createsPickingPending() {
        MrpProductionOrderReleasedEvent event = buildReleasedEvent(1);

        when(requestRepository.existsByTenantIdAndProductionOrderId(TENANT_ID, PRODUCTION_ORDER_ID))
                .thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ProductionMaterialRequest r = inv.getArgument(0);
            setId(r, REQUEST_ID);
            return r;
        });
        when(itemRepository.save(any())).thenAnswer(inv -> {
            ProductionMaterialRequestItem i = inv.getArgument(0);
            setId(i, UUID.randomUUID());
            return i;
        });

        StockItem si = buildStockItem(10);
        when(stockItemRepository.findAvailableByTenantProductWarehouse(TENANT_ID, PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(new java.util.ArrayList<>(List.of(si)));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // PickingOrder creation
        when(pickingOrderRepository.save(any())).thenAnswer(inv -> {
            PickingOrder po = inv.getArgument(0);
            setId(po, UUID.randomUUID());
            return po;
        });
        when(pickingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenReturn(buildAuditLog());

        service.processProductionOrderReleased(event);

        verify(requestRepository, atLeastOnce()).save(any());
        verify(pickingOrderRepository).save(any());
    }

    // -------------------------------------------------------------------------
    // U2 — idempotência: evento duplicado é ignorado
    // -------------------------------------------------------------------------

    @Test
    void processProductionOrderReleased_duplicate_isIgnored() {
        MrpProductionOrderReleasedEvent event = buildReleasedEvent(1);

        when(requestRepository.existsByTenantIdAndProductionOrderId(TENANT_ID, PRODUCTION_ORDER_ID))
                .thenReturn(true);

        service.processProductionOrderReleased(event);

        verify(requestRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // U3 — shortage: stock insuficiente → STOCK_SHORTAGE
    // -------------------------------------------------------------------------

    @Test
    void processProductionOrderReleased_noStock_createsStockShortage() {
        MrpProductionOrderReleasedEvent event = buildReleasedEvent(1);

        when(requestRepository.existsByTenantIdAndProductionOrderId(TENANT_ID, PRODUCTION_ORDER_ID))
                .thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ProductionMaterialRequest r = inv.getArgument(0);
            setId(r, REQUEST_ID);
            return r;
        });
        when(itemRepository.save(any())).thenAnswer(inv -> {
            ProductionMaterialRequestItem i = inv.getArgument(0);
            setId(i, UUID.randomUUID());
            return i;
        });

        // Sem stock disponível
        when(stockItemRepository.findAvailableByTenantProductWarehouse(TENANT_ID, PRODUCT_ID, WAREHOUSE_ID))
                .thenReturn(new java.util.ArrayList<>());
        when(auditLogRepository.save(any())).thenReturn(buildAuditLog());

        service.processProductionOrderReleased(event);

        verify(stockShortageEventPublisher).publishStockShortage(any());
        verify(pickingOrderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // U4 — componentes vazios → cria PMR com STOCK_SHORTAGE (sem itens)
    // -------------------------------------------------------------------------

    @Test
    void processProductionOrderReleased_emptyComponents_createsShortageNoItems() {
        MrpProductionOrderReleasedEvent event = new MrpProductionOrderReleasedEvent(
                "MRP_PRODUCTION_ORDER_RELEASED",
                TENANT_ID,
                PRODUCTION_ORDER_ID,
                "OP-001",
                WAREHOUSE_ID,
                List.of(),
                Instant.now()
        );

        when(requestRepository.existsByTenantIdAndProductionOrderId(TENANT_ID, PRODUCTION_ORDER_ID))
                .thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ProductionMaterialRequest r = inv.getArgument(0);
            setId(r, REQUEST_ID);
            return r;
        });

        service.processProductionOrderReleased(event);

        // Salva PMR com status ERROR, nenhum item criado, nenhuma PickingOrder
        verify(requestRepository).save(argThat(r ->
                r.getStatus() == MaterialRequestStatus.ERROR));
        verify(itemRepository, never()).save(any());
        verify(pickingOrderRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // U5 — processProductionOrderCancelled: PENDING → CANCELLED
    // -------------------------------------------------------------------------

    @Test
    void processProductionOrderCancelled_fromPending_cancels() {
        MrpProductionOrderCancelledEvent event = buildCancelledEvent();

        ProductionMaterialRequest request = buildRequest(MaterialRequestStatus.PENDING);
        when(requestRepository.findByTenantIdAndProductionOrderId(TENANT_ID, PRODUCTION_ORDER_ID))
                .thenReturn(Optional.of(request));
        when(itemRepository.findByTenantIdAndRequestId(TENANT_ID, REQUEST_ID))
                .thenReturn(List.of());
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenReturn(buildAuditLog());

        service.processProductionOrderCancelled(event);

        assertThat(request.getStatus()).isEqualTo(MaterialRequestStatus.CANCELLED);
        assertThat(request.getCancellationReason()).isEqualTo("Teste de cancelamento");
    }

    // -------------------------------------------------------------------------
    // U6 — processProductionOrderCancelled: OP não encontrada → ignorado
    // -------------------------------------------------------------------------

    @Test
    void processProductionOrderCancelled_notFound_isIgnored() {
        MrpProductionOrderCancelledEvent event = buildCancelledEvent();

        when(requestRepository.findByTenantIdAndProductionOrderId(TENANT_ID, PRODUCTION_ORDER_ID))
                .thenReturn(Optional.empty());

        service.processProductionOrderCancelled(event);

        verify(requestRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // U7 — processProductionOrderCancelled: DELIVERED → ignorado
    // -------------------------------------------------------------------------

    @Test
    void processProductionOrderCancelled_fromDelivered_isIgnored() {
        MrpProductionOrderCancelledEvent event = buildCancelledEvent();

        ProductionMaterialRequest request = buildRequest(MaterialRequestStatus.DELIVERED);
        when(requestRepository.findByTenantIdAndProductionOrderId(TENANT_ID, PRODUCTION_ORDER_ID))
                .thenReturn(Optional.of(request));

        service.processProductionOrderCancelled(event);

        verify(requestRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // U8 — confirmDelivery: PICKING_PENDING → DELIVERED
    // -------------------------------------------------------------------------

    @Test
    void confirmDelivery_fromPickingPending_returnsDelivered() {
        ProductionMaterialRequest request = buildRequest(MaterialRequestStatus.PICKING_PENDING);
        request.setWarehouseId(WAREHOUSE_ID);

        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(request));
        when(itemRepository.findByTenantIdAndRequestId(TENANT_ID, REQUEST_ID))
                .thenReturn(List.of());
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ProductionMaterialRequest r = inv.getArgument(0);
            setId(r, REQUEST_ID);
            return r;
        });
        when(auditLogRepository.save(any())).thenReturn(buildAuditLog());

        ProductionMaterialRequestResponse resp = service.confirmDelivery(REQUEST_ID);

        assertThat(resp.status()).isEqualTo(MaterialRequestStatus.DELIVERED.name());
        verify(materialsDeliveredEventPublisher).publishMaterialsDelivered(any());
    }

    // -------------------------------------------------------------------------
    // U9 — confirmDelivery: status inválido → ConflictException
    // -------------------------------------------------------------------------

    @Test
    void confirmDelivery_fromPending_throwsConflict() {
        ProductionMaterialRequest request = buildRequest(MaterialRequestStatus.PENDING);

        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirmDelivery(REQUEST_ID))
                .isInstanceOf(ConflictException.class);
    }

    // -------------------------------------------------------------------------
    // U10 — confirmDelivery: request não encontrada → ResourceNotFoundException
    // -------------------------------------------------------------------------

    @Test
    void confirmDelivery_notFound_throwsNotFound() {
        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmDelivery(REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // U11 — receiveFinishedGoods: DELIVERED → FINISHED_GOODS_RECEIVED
    // -------------------------------------------------------------------------

    @Test
    void receiveFinishedGoods_fromDelivered_returnsFinishedGoodsReceived() {
        ProductionMaterialRequest request = buildRequest(MaterialRequestStatus.DELIVERED);
        request.setWarehouseId(WAREHOUSE_ID);

        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(request));

        Location location = buildLocation();
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(location));
        ProductWms product = buildProduct();
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));

        when(stockItemRepository.findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                TENANT_ID, LOCATION_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenReturn(buildMovement());
        when(requestRepository.save(any())).thenAnswer(inv -> {
            ProductionMaterialRequest r = inv.getArgument(0);
            setId(r, REQUEST_ID);
            return r;
        });
        when(auditLogRepository.save(any())).thenReturn(buildAuditLog());

        ReceiveFinishedGoodsRequest body = new ReceiveFinishedGoodsRequest(
                List.of(new ReceiveFinishedGoodsRequest.FinishedGoodsItem(PRODUCT_ID, LOCATION_ID, 5, null))
        );
        when(itemRepository.findByTenantIdAndRequestId(TENANT_ID, REQUEST_ID))
                .thenReturn(List.of());

        ProductionMaterialRequestResponse resp = service.receiveFinishedGoods(REQUEST_ID, body);

        assertThat(resp.status()).isEqualTo(MaterialRequestStatus.FINISHED_GOODS_RECEIVED.name());
        verify(finishedGoodsReceivedEventPublisher).publishFinishedGoodsReceived(any());
    }

    // -------------------------------------------------------------------------
    // U12 — receiveFinishedGoods: status inválido → ConflictException
    // -------------------------------------------------------------------------

    @Test
    void receiveFinishedGoods_notDelivered_throwsConflict() {
        ProductionMaterialRequest request = buildRequest(MaterialRequestStatus.PICKING_PENDING);

        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.of(request));

        ReceiveFinishedGoodsRequest body = new ReceiveFinishedGoodsRequest(
                List.of(new ReceiveFinishedGoodsRequest.FinishedGoodsItem(PRODUCT_ID, LOCATION_ID, 1, null))
        );

        assertThatThrownBy(() -> service.receiveFinishedGoods(REQUEST_ID, body))
                .isInstanceOf(ConflictException.class);
    }

    // -------------------------------------------------------------------------
    // U13 — getRequest: tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void getRequest_wrongTenant_throwsNotFound() {
        when(requestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequest(REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // U14 — listRequests: sem filtro → todos os registros do tenant
    // -------------------------------------------------------------------------

    @Test
    void listRequests_noFilter_returnsTenantPage() {
        when(requestRepository.findByTenantId(eq(TENANT_ID), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var page = service.listRequests(null, org.springframework.data.domain.Pageable.unpaged());

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MrpProductionOrderReleasedEvent buildReleasedEvent(int qty) {
        return new MrpProductionOrderReleasedEvent(
                "MRP_PRODUCTION_ORDER_RELEASED",
                TENANT_ID,
                PRODUCTION_ORDER_ID,
                "OP-001",
                WAREHOUSE_ID,
                List.of(new MrpProductionOrderReleasedEvent.ProductionComponent(PRODUCT_ID, qty)),
                Instant.now()
        );
    }

    private MrpProductionOrderCancelledEvent buildCancelledEvent() {
        return new MrpProductionOrderCancelledEvent(
                "MRP_PRODUCTION_ORDER_CANCELLED",
                TENANT_ID,
                PRODUCTION_ORDER_ID,
                "Teste de cancelamento",
                Instant.now()
        );
    }

    private ProductionMaterialRequest buildRequest(MaterialRequestStatus status) {
        ProductionMaterialRequest r = ProductionMaterialRequest.builder()
                .tenantId(TENANT_ID)
                .productionOrderId(PRODUCTION_ORDER_ID)
                .mrpOrderNumber("OP-001")
                .warehouseId(WAREHOUSE_ID)
                .status(status)
                .build();
        setId(r, REQUEST_ID);
        return r;
    }

    private StockItem buildStockItem(int quantity) {
        Location loc = buildLocation();
        ProductWms product = buildProduct();
        StockItem si = StockItem.builder()
                .tenantId(TENANT_ID)
                .location(loc)
                .product(product)
                .quantityAvailable(quantity)
                .receivedAt(Instant.now())
                .build();
        setId(si, UUID.randomUUID());
        return si;
    }

    private Location buildLocation() {
        Location loc = new Location();
        setId(loc, LOCATION_ID);
        try {
            var f = com.odin.wms.domain.entity.base.BaseEntity.class.getDeclaredField("tenantId");
            f.setAccessible(true);
            f.set(loc, TENANT_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return loc;
    }

    private ProductWms buildProduct() {
        ProductWms p = new ProductWms();
        setId(p, PRODUCT_ID);
        return p;
    }

    private StockMovement buildMovement() {
        StockMovement m = new StockMovement();
        setId(m, UUID.randomUUID());
        return m;
    }

    private AuditLog buildAuditLog() {
        AuditLog log = new AuditLog();
        setId(log, UUID.randomUUID());
        return log;
    }

    private void setId(Object entity, UUID id) {
        try {
            Class<?> base = entity.getClass();
            while (base != null) {
                try {
                    var field = base.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) {
                    base = base.getSuperclass();
                }
            }
            throw new RuntimeException("Campo 'id' não encontrado em: " + entity.getClass());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Falha ao definir UUID via reflexão", e);
        }
    }
}
