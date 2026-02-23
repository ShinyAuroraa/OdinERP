package com.odin.wms.shipping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.ShippingOrder.ShippingStatus;
import com.odin.wms.domain.enums.AuditAction;
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
import com.odin.wms.service.ShippingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ShippingService — U1-U18.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingServiceTest {

    @Mock private ShippingOrderRepository shippingOrderRepository;
    @Mock private ShippingItemRepository shippingItemRepository;
    @Mock private PackingItemRepository packingItemRepository;
    @Mock private ProductWmsRepository productWmsRepository;
    @Mock private LotRepository lotRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogIndexer auditLogIndexer;
    @Mock private ShippingCompletedEventPublisher shippingCompletedEventPublisher;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ShippingService shippingService;

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID      = UUID.randomUUID();
    private static final UUID PRODUCT_ID        = UUID.randomUUID();
    private static final UUID LOT_ID            = UUID.randomUUID();
    private static final UUID ORDER_ID          = UUID.randomUUID();
    private static final UUID ITEM_ID           = UUID.randomUUID();
    private static final UUID PACKING_ORDER_ID  = UUID.randomUUID();
    private static final UUID PACKING_ITEM_ID   = UUID.randomUUID();
    private static final UUID PICKING_ORDER_ID  = UUID.randomUUID();
    private static final UUID OPERATOR_ID       = UUID.randomUUID();
    private static final UUID CRM_ORDER_ID      = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);

        // Mock JWT authentication
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(OPERATOR_ID.toString());
        JwtAuthenticationToken jwtAuth = mock(JwtAuthenticationToken.class);
        when(jwtAuth.getToken()).thenReturn(jwt);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(jwtAuth);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // U1 — createShippingOrderFromKafka_validEvent_createsPendingShippingOrder
    // =========================================================================

    @Test
    void createShippingOrderFromKafka_validEvent_createsPendingShippingOrder() {
        PackingCompletedEvent event = buildPackingCompletedEvent();
        PackingItem packingItem = buildPackingItem();

        when(shippingOrderRepository.findByTenantIdAndPackingOrderId(TENANT_ID, PACKING_ORDER_ID))
                .thenReturn(Optional.empty());
        when(shippingOrderRepository.save(any()))
                .thenAnswer(inv -> setId(inv.getArgument(0), ORDER_ID));
        when(packingItemRepository.findByTenantIdAndPackingOrderId(TENANT_ID, PACKING_ORDER_ID))
                .thenReturn(List.of(packingItem));
        when(shippingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        shippingService.createShippingOrderFromKafka(event);

        ArgumentCaptor<ShippingOrder> orderCaptor = ArgumentCaptor.forClass(ShippingOrder.class);
        verify(shippingOrderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(ShippingStatus.PENDING);
        assertThat(orderCaptor.getValue().getPackingOrderId()).isEqualTo(PACKING_ORDER_ID);

        ArgumentCaptor<List> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(shippingItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
    }

    // =========================================================================
    // U2 — createShippingOrderFromKafka_duplicatePackingOrderId_isIdempotent
    // =========================================================================

    @Test
    void createShippingOrderFromKafka_duplicatePackingOrderId_isIdempotent() {
        PackingCompletedEvent event = buildPackingCompletedEvent();
        ShippingOrder existing = buildPendingOrder();

        when(shippingOrderRepository.findByTenantIdAndPackingOrderId(TENANT_ID, PACKING_ORDER_ID))
                .thenReturn(Optional.of(existing));

        shippingService.createShippingOrderFromKafka(event);

        verify(shippingOrderRepository, never()).save(any());
        verify(shippingItemRepository, never()).saveAll(any());
    }

    // =========================================================================
    // U3 — startLoading_pendingOrder_startsSuccessfully
    // =========================================================================

    @Test
    void startLoading_pendingOrder_startsSuccessfully() {
        ShippingOrder order = buildPendingOrder();
        StartLoadingRequest req = new StartLoadingRequest(OPERATOR_ID);

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shippingItemRepository.findByTenantIdAndShippingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of());

        ShippingOrderResponse resp = shippingService.startLoading(ORDER_ID, req);

        assertThat(resp.status()).isEqualTo(ShippingStatus.IN_PROGRESS);
        assertThat(resp.operatorId()).isEqualTo(OPERATOR_ID);
    }

    // =========================================================================
    // U4 — startLoading_nonPendingOrder_throwsBusinessException
    // =========================================================================

    @Test
    void startLoading_nonPendingOrder_throwsBusinessException() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        StartLoadingRequest req = new StartLoadingRequest(OPERATOR_ID);

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> shippingService.startLoading(ORDER_ID, req))
                .isInstanceOf(ConflictException.class);
    }

    // =========================================================================
    // U5 — loadItem_validSkuBarcode_marksLoaded
    // =========================================================================

    @Test
    void loadItem_validSkuBarcode_marksLoaded() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        ShippingItem item = buildUnloadedItem();
        ProductWms product = buildProduct("SKU-001");
        LoadItemRequest req = new LoadItemRequest("SKU-001");

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
                .thenReturn(Optional.of(item));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));
        when(shippingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShippingItemResponse resp = shippingService.loadItem(ORDER_ID, ITEM_ID, req);

        assertThat(resp.loaded()).isTrue();
        assertThat(resp.loadedAt()).isNotNull();
        assertThat(resp.loadedBy()).isEqualTo(OPERATOR_ID);
    }

    // =========================================================================
    // U6 — loadItem_validLotBarcode_marksLoaded
    // =========================================================================

    @Test
    void loadItem_validLotBarcode_marksLoaded() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        ShippingItem item = buildUnloadedItemWithLot();
        ProductWms product = buildProduct("SKU-001");
        Lot lot = buildLot("LOT-001");
        LoadItemRequest req = new LoadItemRequest("LOT-001");

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
                .thenReturn(Optional.of(item));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));
        when(lotRepository.findById(LOT_ID)).thenReturn(Optional.of(lot));
        when(shippingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShippingItemResponse resp = shippingService.loadItem(ORDER_ID, ITEM_ID, req);

        assertThat(resp.loaded()).isTrue();
    }

    // =========================================================================
    // U7 — loadItem_wrongBarcode_throwsBusinessException
    // =========================================================================

    @Test
    void loadItem_wrongBarcode_throwsBusinessException() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        ShippingItem item = buildUnloadedItem();
        ProductWms product = buildProduct("SKU-001");
        LoadItemRequest req = new LoadItemRequest("WRONG-BARCODE");

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
                .thenReturn(Optional.of(item));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));

        assertThatThrownBy(() -> shippingService.loadItem(ORDER_ID, ITEM_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não corresponde");
    }

    // =========================================================================
    // U8 — loadItem_alreadyLoaded_throwsBusinessException
    // =========================================================================

    @Test
    void loadItem_alreadyLoaded_throwsBusinessException() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        ShippingItem item = buildLoadedItem();
        LoadItemRequest req = new LoadItemRequest("SKU-001");

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByIdAndTenantId(ITEM_ID, TENANT_ID))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> shippingService.loadItem(ORDER_ID, ITEM_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já foi carregado");
    }

    // =========================================================================
    // U9 — setCarrierDetails_inProgress_updatesNonNullFields
    // =========================================================================

    @Test
    void setCarrierDetails_inProgress_updatesNonNullFields() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        order.setCarrierName("OLD");
        SetCarrierDetailsRequest req = new SetCarrierDetailsRequest(
                "Transportadora XYZ", null, "João", null, null);

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShippingOrderResponse resp = shippingService.setCarrierDetails(ORDER_ID, req);

        assertThat(resp.carrierName()).isEqualTo("Transportadora XYZ");
        assertThat(resp.driverName()).isEqualTo("João");
        assertThat(resp.vehiclePlate()).isNull(); // não atualizado
    }

    // =========================================================================
    // U10 — generateManifest_allItemsPresent_returnsManifestJson
    // =========================================================================

    @Test
    void generateManifest_allItemsPresent_returnsManifestJson() throws Exception {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        ShippingItem item = buildLoadedItem();

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByTenantIdAndShippingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(item));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"shippingOrderId\":\"" + ORDER_ID + "\"}");
        when(shippingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ShippingManifestResponse resp = shippingService.generateManifest(ORDER_ID);

        assertThat(resp.manifestJson()).contains(ORDER_ID.toString());
        assertThat(resp.manifestGeneratedAt()).isNotNull();
        assertThat(resp.itemCount()).isEqualTo(1);
    }

    // =========================================================================
    // U11 — generateManifest_alreadyGenerated_returnsExistingManifest
    // =========================================================================

    @Test
    void generateManifest_alreadyGenerated_returnsExistingManifest() throws Exception {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        order.setManifestJson("{\"existing\":true}");
        order.setManifestGeneratedAt(Instant.now().minusSeconds(60));
        ShippingItem item = buildLoadedItem();

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByTenantIdAndShippingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(item));

        ShippingManifestResponse resp = shippingService.generateManifest(ORDER_ID);

        assertThat(resp.manifestJson()).isEqualTo("{\"existing\":true}");
        verify(objectMapper, never()).writeValueAsString(any());
    }

    // =========================================================================
    // U12 — dispatch_allLoaded_withCarrierName_dispatchesAndPublishesEvent
    // =========================================================================

    @Test
    void dispatch_allLoaded_withCarrierName_dispatchesAndPublishesEvent() {
        ShippingOrder order = buildOrderWithCarrierAndOperator();
        ShippingItem item = buildLoadedItem();
        ProductWms product = buildProduct("SKU-001");
        AuditLog savedLog = AuditLog.builder()
                .tenantId(TENANT_ID).entityType("SHIPPING_ORDER").entityId(ORDER_ID)
                .action(AuditAction.MOVEMENT).actorId(OPERATOR_ID).build();

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByTenantIdAndShippingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(item));
        when(shippingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenReturn(savedLog);
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));

        ShippingOrderResponse resp = shippingService.dispatchShipping(ORDER_ID);

        assertThat(resp.status()).isEqualTo(ShippingStatus.DISPATCHED);
        assertThat(resp.dispatchedAt()).isNotNull();
        verify(stockMovementRepository).save(any());
        verify(auditLogIndexer).indexAuditLogAsync(any());
        verify(shippingCompletedEventPublisher).publishShippingCompleted(any());
    }

    // =========================================================================
    // U13 — dispatch_itemNotLoaded_throwsBusinessException
    // =========================================================================

    @Test
    void dispatch_itemNotLoaded_throwsBusinessException() {
        ShippingOrder order = buildOrderWithCarrierAndOperator();
        ShippingItem item = buildUnloadedItem();

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingItemRepository.findByTenantIdAndShippingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> shippingService.dispatchShipping(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("itens não carregados");
    }

    // =========================================================================
    // U14 — dispatch_noCarrierName_throwsBusinessException
    // =========================================================================

    @Test
    void dispatch_noCarrierName_throwsBusinessException() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        order.setCarrierName(null);

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> shippingService.dispatchShipping(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("transportadora");
    }

    // =========================================================================
    // U15 — cancelShipping_inProgress_cancelledSuccessfully
    // =========================================================================

    @Test
    void cancelShipping_inProgress_cancelledSuccessfully() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        CancelShippingOrderRequest req = new CancelShippingOrderRequest("Pedido cancelado pelo cliente");

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));
        when(shippingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenReturn(mock(AuditLog.class));
        when(shippingItemRepository.findByTenantIdAndShippingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of());

        ShippingOrderResponse resp = shippingService.cancelShipping(ORDER_ID, req);

        assertThat(resp.status()).isEqualTo(ShippingStatus.CANCELLED);
        assertThat(resp.cancellationReason()).isEqualTo("Pedido cancelado pelo cliente");
        verify(auditLogRepository).save(any());
    }

    // =========================================================================
    // U16 — cancelShipping_dispatched_throwsBusinessException
    // =========================================================================

    @Test
    void cancelShipping_dispatched_throwsBusinessException() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.DISPATCHED);
        CancelShippingOrderRequest req = new CancelShippingOrderRequest("Tentativa cancelamento");

        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> shippingService.cancelShipping(ORDER_ID, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DISPATCHED");
    }

    // =========================================================================
    // U17 — getOrder_wrongTenant_throws403
    // =========================================================================

    @Test
    void getOrder_wrongTenant_throws403() {
        // findByIdAndTenantId retorna vazio (tenant errado), mas existsById retorna true
        when(shippingOrderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID))
                .thenReturn(Optional.empty());
        when(shippingOrderRepository.existsById(ORDER_ID)).thenReturn(true);

        assertThatThrownBy(() -> shippingService.getOrder(ORDER_ID))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // =========================================================================
    // U18 — listOrders_byStatus_returnsPaged
    // =========================================================================

    @Test
    void listOrders_byStatus_returnsPaged() {
        ShippingOrder order = buildPendingOrder();
        Page<ShippingOrder> page = new PageImpl<>(List.of(order));

        when(shippingOrderRepository.findByTenantIdAndStatus(eq(TENANT_ID),
                eq(ShippingStatus.PENDING), any(Pageable.class))).thenReturn(page);

        Page<ShippingOrderResponse> result =
                shippingService.listOrders(ShippingStatus.PENDING, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).items()).isNull(); // sem itens no listing
    }

    // =========================================================================
    // Builders
    // =========================================================================

    private PackingCompletedEvent buildPackingCompletedEvent() {
        return new PackingCompletedEvent(
                "PACKING_ORDER_COMPLETED", TENANT_ID, PACKING_ORDER_ID, PICKING_ORDER_ID,
                CRM_ORDER_ID, WAREHOUSE_ID, OPERATOR_ID, "SSCC123", null, null, "COMPLETED",
                List.of(new PackingCompletedEvent.PackingCompletedItem(PRODUCT_ID, null, 3)),
                Instant.now()
        );
    }

    private PackingItem buildPackingItem() {
        PackingItem item = PackingItem.builder()
                .tenantId(TENANT_ID)
                .packingOrderId(PACKING_ORDER_ID)
                .pickingItemId(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .lotId(null)
                .quantityPacked(3)
                .build();
        ReflectionTestUtils.setField(item, "id", PACKING_ITEM_ID);
        return item;
    }

    private ShippingOrder buildPendingOrder() {
        ShippingOrder order = ShippingOrder.builder()
                .tenantId(TENANT_ID)
                .packingOrderId(PACKING_ORDER_ID)
                .pickingOrderId(PICKING_ORDER_ID)
                .warehouseId(WAREHOUSE_ID)
                .crmOrderId(CRM_ORDER_ID)
                .status(ShippingStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private ShippingOrder buildOrderWithStatus(ShippingStatus status) {
        ShippingOrder order = ShippingOrder.builder()
                .tenantId(TENANT_ID)
                .packingOrderId(PACKING_ORDER_ID)
                .warehouseId(WAREHOUSE_ID)
                .status(status)
                .build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private ShippingOrder buildOrderWithCarrierAndOperator() {
        ShippingOrder order = buildOrderWithStatus(ShippingStatus.IN_PROGRESS);
        order.setCarrierName("Transportadora XYZ");
        order.setOperatorId(OPERATOR_ID);
        return order;
    }

    private ShippingItem buildUnloadedItem() {
        ShippingItem item = ShippingItem.builder()
                .tenantId(TENANT_ID)
                .shippingOrderId(ORDER_ID)
                .packingItemId(PACKING_ITEM_ID)
                .productId(PRODUCT_ID)
                .quantityShipped(3)
                .loaded(false)
                .build();
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    private ShippingItem buildUnloadedItemWithLot() {
        ShippingItem item = ShippingItem.builder()
                .tenantId(TENANT_ID)
                .shippingOrderId(ORDER_ID)
                .packingItemId(PACKING_ITEM_ID)
                .productId(PRODUCT_ID)
                .lotId(LOT_ID)
                .quantityShipped(3)
                .loaded(false)
                .build();
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    private ShippingItem buildLoadedItem() {
        ShippingItem item = ShippingItem.builder()
                .tenantId(TENANT_ID)
                .shippingOrderId(ORDER_ID)
                .packingItemId(PACKING_ITEM_ID)
                .productId(PRODUCT_ID)
                .quantityShipped(3)
                .loaded(true)
                .loadedAt(Instant.now().minusSeconds(30))
                .loadedBy(OPERATOR_ID)
                .build();
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    private ProductWms buildProduct(String sku) {
        ProductWms product = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(UUID.randomUUID())
                .sku(sku)
                .name("Produto Teste")
                .build();
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private Lot buildLot(String lotNumber) {
        return Lot.builder()
                .tenantId(TENANT_ID)
                .lotNumber(lotNumber)
                .build();
    }

    private ShippingOrder setId(ShippingOrder order, UUID id) {
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
