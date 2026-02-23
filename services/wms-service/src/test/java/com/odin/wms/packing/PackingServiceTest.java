package com.odin.wms.packing;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PackingOrder.PackingStatus;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.PackingItemResponse;
import com.odin.wms.dto.response.PackingLabelResponse;
import com.odin.wms.dto.response.PackingOrderResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.messaging.PackingCompletedEventPublisher;
import com.odin.wms.messaging.event.PackingCompletedEvent;
import com.odin.wms.messaging.event.PickingCompletedEvent;
import com.odin.wms.service.PackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para PackingService — U1-U18.
 */
@ExtendWith(MockitoExtension.class)
class PackingServiceTest {

    @Mock private PackingOrderRepository packingOrderRepository;
    @Mock private PackingItemRepository packingItemRepository;
    @Mock private ProductWmsRepository productWmsRepository;
    @Mock private LotRepository lotRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private PackingCompletedEventPublisher packingCompletedEventPublisher;

    @InjectMocks
    private PackingService packingService;

    private static final UUID TENANT_ID        = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID     = UUID.randomUUID();
    private static final UUID PRODUCT_ID       = UUID.randomUUID();
    private static final UUID LOT_ID           = UUID.randomUUID();
    private static final UUID ORDER_ID         = UUID.randomUUID();
    private static final UUID ITEM_ID          = UUID.randomUUID();
    private static final UUID PICKING_ORDER_ID = UUID.randomUUID();
    private static final UUID PICKING_ITEM_ID  = UUID.randomUUID();
    private static final UUID OPERATOR_ID      = UUID.randomUUID();
    private static final UUID CRM_ORDER_ID     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // =========================================================================
    // U1 — createPackingOrder_fromEvent_success
    // =========================================================================

    @Test
    void createPackingOrder_fromEvent_success() {
        when(packingOrderRepository.findByTenantIdAndPickingOrderId(TENANT_ID, PICKING_ORDER_ID))
                .thenReturn(Optional.empty());
        when(packingOrderRepository.save(any())).thenAnswer(inv -> {
            PackingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID);
            return o;
        });
        when(packingItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        PickingCompletedEvent event = buildPickingCompletedEvent(List.of(
                new PickingCompletedEvent.PickingCompletedItem(
                        PICKING_ITEM_ID, PRODUCT_ID, LOT_ID, UUID.randomUUID(), 5, 5)
        ));

        packingService.createPackingOrderFromKafka(event);

        verify(packingOrderRepository).save(argThat(o ->
                o.getStatus() == PackingStatus.PENDING
                && o.getPickingOrderId().equals(PICKING_ORDER_ID)
                && o.getTenantId().equals(TENANT_ID)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PackingItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(packingItemRepository).saveAll(captor.capture());
        List<PackingItem> items = captor.getValue();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).isScanned()).isFalse();
        assertThat(items.get(0).getQuantityPacked()).isEqualTo(5);
        assertThat(items.get(0).getPickingItemId()).isEqualTo(PICKING_ITEM_ID);
    }

    // =========================================================================
    // U2 — createPackingOrder_duplicatePickingOrderId_isIdempotent
    // =========================================================================

    @Test
    void createPackingOrder_duplicatePickingOrderId_isIdempotent() {
        PackingOrder existing = buildOrder(PackingStatus.PENDING);
        when(packingOrderRepository.findByTenantIdAndPickingOrderId(TENANT_ID, PICKING_ORDER_ID))
                .thenReturn(Optional.of(existing));

        PickingCompletedEvent event = buildPickingCompletedEvent(List.of(
                new PickingCompletedEvent.PickingCompletedItem(
                        PICKING_ITEM_ID, PRODUCT_ID, null, UUID.randomUUID(), 3, 3)
        ));

        // Deve retornar silenciosamente sem exceção
        assertThatCode(() -> packingService.createPackingOrderFromKafka(event))
                .doesNotThrowAnyException();

        verify(packingOrderRepository, never()).save(any());
        verify(packingItemRepository, never()).saveAll(any());
    }

    // =========================================================================
    // U3 — openPacking_pendingOrder_changesStatusToInProgress
    // =========================================================================

    @Test
    void openPacking_pendingOrder_changesStatusToInProgress() {
        PackingOrder order = buildOrder(PackingStatus.PENDING);
        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(packingItemRepository.findByTenantIdAndPackingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of());

        PackingOrderResponse resp = packingService.openPacking(ORDER_ID,
                new OpenPackingRequest(OPERATOR_ID));

        assertThat(resp.status()).isEqualTo(PackingStatus.IN_PROGRESS.name());
        assertThat(resp.operatorId()).isEqualTo(OPERATOR_ID);

        verify(packingOrderRepository).save(argThat(o ->
                o.getStatus() == PackingStatus.IN_PROGRESS
                && o.getOperatorId().equals(OPERATOR_ID)));
    }

    // =========================================================================
    // U4 — openPacking_nonPendingOrder_throwsConflictException
    // =========================================================================

    @Test
    void openPacking_nonPendingOrder_throwsConflictException() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                packingService.openPacking(ORDER_ID, new OpenPackingRequest(OPERATOR_ID)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PENDING");
    }

    // =========================================================================
    // U5 — scanItem_validBarcodeMatchesSku_marksScanned
    // =========================================================================

    @Test
    void scanItem_validBarcodeMatchesSku_marksScanned() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        PackingItem item = buildItem(ORDER_ID, PRODUCT_ID, null);
        ProductWms product = buildProduct("SKU-001");

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingItemRepository.findByTenantIdAndId(TENANT_ID, ITEM_ID))
                .thenReturn(Optional.of(item));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));
        when(packingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Case-insensitive — SKU em minúsculas
        PackingItemResponse resp = packingService.scanItem(ORDER_ID, ITEM_ID,
                new ScanItemRequest("sku-001", OPERATOR_ID));

        assertThat(resp.scanned()).isTrue();
        assertThat(resp.scannedBy()).isEqualTo(OPERATOR_ID);
        verify(packingItemRepository).save(argThat(i -> i.isScanned() && i.getScannedAt() != null));
    }

    // =========================================================================
    // U6 — scanItem_validBarcodeMatchesLotNumber_marksScanned
    // =========================================================================

    @Test
    void scanItem_validBarcodeMatchesLotNumber_marksScanned() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        PackingItem item = buildItem(ORDER_ID, PRODUCT_ID, LOT_ID);
        ProductWms product = buildProduct("SKU-002");
        Lot lot = buildLot("LOT-ABC");

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingItemRepository.findByTenantIdAndId(TENANT_ID, ITEM_ID))
                .thenReturn(Optional.of(item));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));
        when(lotRepository.findById(LOT_ID))
                .thenReturn(Optional.of(lot));
        when(packingItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Barcode = lotNumber (case-insensitive)
        PackingItemResponse resp = packingService.scanItem(ORDER_ID, ITEM_ID,
                new ScanItemRequest("lot-abc", OPERATOR_ID));

        assertThat(resp.scanned()).isTrue();
    }

    // =========================================================================
    // U7 — scanItem_barcodeNotMatching_throwsIllegalArgumentException
    // =========================================================================

    @Test
    void scanItem_barcodeNotMatching_throwsIllegalArgumentException() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        PackingItem item = buildItem(ORDER_ID, PRODUCT_ID, null);
        ProductWms product = buildProduct("SKU-XYZ");

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingItemRepository.findByTenantIdAndId(TENANT_ID, ITEM_ID))
                .thenReturn(Optional.of(item));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
                packingService.scanItem(ORDER_ID, ITEM_ID,
                        new ScanItemRequest("WRONG-BARCODE", OPERATOR_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU-XYZ");
    }

    // =========================================================================
    // U8 — scanItem_alreadyScanned_throwsConflictException
    // =========================================================================

    @Test
    void scanItem_alreadyScanned_throwsConflictException() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        PackingItem item = buildItem(ORDER_ID, PRODUCT_ID, null);
        item.setScanned(true);
        item.setScannedAt(Instant.now());

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingItemRepository.findByTenantIdAndId(TENANT_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() ->
                packingService.scanItem(ORDER_ID, ITEM_ID,
                        new ScanItemRequest("BARCODE", OPERATOR_ID)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("escaneado");
    }

    // =========================================================================
    // U9 — setPackageDetails_validData_updatesOnlyNonNullFields
    // =========================================================================

    @Test
    void setPackageDetails_validData_updatesOnlyNonNullFields() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        // Dimensões inicialmente null
        assertThat(order.getLengthCm()).isNull();

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Apenas weightKg e packageType informados — dimensões omitidas (null)
        PackingOrderResponse resp = packingService.setPackageDetails(ORDER_ID,
                new SetPackageDetailsRequest(2.5, "BOX", null, null, null, "Fragile"));

        assertThat(resp.weightKg()).isEqualByComparingTo(BigDecimal.valueOf(2.5));
        assertThat(resp.packageType()).isEqualTo("BOX");
        // Dimensões não informadas → permanecem null
        assertThat(resp.lengthCm()).isNull();
        assertThat(resp.widthCm()).isNull();
        assertThat(resp.heightCm()).isNull();
        assertThat(resp.notes()).isEqualTo("Fragile");
    }

    // =========================================================================
    // U10 — setPackageDetails_negativeWeight_throwsIllegalArgumentException
    // =========================================================================

    @Test
    void setPackageDetails_negativeWeight_throwsIllegalArgumentException() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                packingService.setPackageDetails(ORDER_ID,
                        new SetPackageDetailsRequest(-1.0, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    // =========================================================================
    // U11 — generateLabel_generatesSSCC18Digits_andBase64Barcode
    // =========================================================================

    @Test
    void generateLabel_generatesSSCC18Digits_andBase64Barcode() {
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        // SSCC ainda não gerado
        assertThat(order.getSscc()).isNull();

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PackingLabelResponse resp = packingService.generateLabel(ORDER_ID);

        assertThat(resp.sscc()).hasSize(18);
        assertThat(resp.barcodeBase64()).isNotBlank();
        assertThat(resp.barcodeFormat()).isEqualTo("GS1_128");
        // SSCC deve ser numérico
        assertThatCode(() -> Long.parseLong(resp.sscc())).doesNotThrowAnyException();
        // Save foi chamado para persistir o novo SSCC
        verify(packingOrderRepository).save(argThat(o -> o.getSscc() != null));
    }

    // =========================================================================
    // U12 — generateLabel_calledTwice_returnsExistingSSCC
    // =========================================================================

    @Test
    void generateLabel_calledTwice_returnsExistingSSCC() {
        // SSCC já existente no banco
        final String existingSscc = "000000001234567890".substring(0, 18); // 18 chars
        PackingOrder order = buildOrder(PackingStatus.IN_PROGRESS);
        order.setSscc(existingSscc);

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));

        PackingLabelResponse resp = packingService.generateLabel(ORDER_ID);

        assertThat(resp.sscc()).isEqualTo(existingSscc);
        assertThat(resp.barcodeBase64()).isNotBlank();
        // Save NÃO chamado — SSCC já existe
        verify(packingOrderRepository, never()).save(any());
    }

    // =========================================================================
    // U13 — completePacking_allScanned_withSSCC_publishesEventAndAuditLog
    // =========================================================================

    @Test
    void completePacking_allScanned_withSSCC_publishesEventAndAuditLog() {
        PackingOrder order = buildOrderInProgress();
        order.setSscc("012345678901234560");

        PackingItem item1 = buildItem(ORDER_ID, PRODUCT_ID, null);
        item1.setScanned(true);
        item1.setScannedAt(Instant.now());

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingItemRepository.findByTenantIdAndPackingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(item1));
        when(packingOrderRepository.save(any())).thenAnswer(inv -> {
            PackingOrder o = inv.getArgument(0);
            setEntityId(o, ORDER_ID);
            return o;
        });
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(packingCompletedEventPublisher).publishPackingCompleted(any());

        PackingOrderResponse resp = packingService.completePacking(ORDER_ID);

        assertThat(resp.status()).isEqualTo(PackingStatus.COMPLETED.name());

        // AuditLog verificado
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getEntityType()).isEqualTo("PACKING_ORDER");
        assertThat(audit.getAction()).isEqualTo(AuditAction.MOVEMENT);
        assertThat(audit.getEntityId()).isEqualTo(ORDER_ID);

        // Publisher chamado
        ArgumentCaptor<PackingCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(PackingCompletedEvent.class);
        verify(packingCompletedEventPublisher).publishPackingCompleted(eventCaptor.capture());
        PackingCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.tenantId()).isEqualTo(TENANT_ID);
        assertThat(publishedEvent.sscc()).isEqualTo("012345678901234560");
        assertThat(publishedEvent.status()).isEqualTo("COMPLETED");
    }

    // =========================================================================
    // U14 — completePacking_notAllScanned_throwsBusinessException
    // =========================================================================

    @Test
    void completePacking_notAllScanned_throwsBusinessException() {
        PackingOrder order = buildOrderInProgress();
        order.setSscc("012345678901234560");

        PackingItem scannedItem = buildItem(ORDER_ID, PRODUCT_ID, null);
        scannedItem.setScanned(true);

        PackingItem unscannedItem = buildItem(ORDER_ID, UUID.randomUUID(), null);
        // unscannedItem.scanned = false (default)

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingItemRepository.findByTenantIdAndPackingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(scannedItem, unscannedItem));

        assertThatThrownBy(() -> packingService.completePacking(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não verificados");
    }

    // =========================================================================
    // U15 — completePacking_noSSCC_throwsBusinessException
    // =========================================================================

    @Test
    void completePacking_noSSCC_throwsBusinessException() {
        PackingOrder order = buildOrderInProgress();
        // SSCC = null

        PackingItem item = buildItem(ORDER_ID, PRODUCT_ID, null);
        item.setScanned(true);

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingItemRepository.findByTenantIdAndPackingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> packingService.completePacking(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Etiqueta não gerada");
    }

    // =========================================================================
    // U16 — cancelPacking_inProgress_cancelledSuccessfully
    // =========================================================================

    @Test
    void cancelPacking_inProgress_cancelledSuccessfully() {
        PackingOrder order = buildOrderInProgress();

        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));
        when(packingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(packingItemRepository.findByTenantIdAndPackingOrderId(TENANT_ID, ORDER_ID))
                .thenReturn(List.of());
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PackingOrderResponse resp = packingService.cancelPacking(ORDER_ID,
                new CancelPackingOrderRequest("Cliente solicitou cancelamento"));

        assertThat(resp.status()).isEqualTo(PackingStatus.CANCELLED.name());
        assertThat(resp.cancellationReason()).isEqualTo("Cliente solicitou cancelamento");

        // AuditLog verificado
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(captor.getValue().getEntityType()).isEqualTo("PACKING_ORDER");
    }

    // =========================================================================
    // U17 — cancelPacking_completed_throwsConflictException
    // =========================================================================

    @Test
    void cancelPacking_completed_throwsConflictException() {
        PackingOrder order = buildOrder(PackingStatus.COMPLETED);
        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                packingService.cancelPacking(ORDER_ID,
                        new CancelPackingOrderRequest("motivo")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("completado");
    }

    // =========================================================================
    // U18 — getOrder_wrongTenant_throws403
    // =========================================================================

    @Test
    void getOrder_wrongTenant_throws403() {
        // Ordem existe no DB mas pertence a outro tenant
        when(packingOrderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID))
                .thenReturn(Optional.empty());
        when(packingOrderRepository.existsById(ORDER_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> packingService.getOrder(ORDER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Acesso negado");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PackingOrder buildOrder(PackingStatus status) {
        PackingOrder o = PackingOrder.builder()
                .tenantId(TENANT_ID)
                .warehouseId(WAREHOUSE_ID)
                .pickingOrderId(PICKING_ORDER_ID)
                .crmOrderId(CRM_ORDER_ID)
                .status(status)
                .build();
        setEntityId(o, ORDER_ID);
        return o;
    }

    private PackingOrder buildOrderInProgress() {
        PackingOrder o = buildOrder(PackingStatus.IN_PROGRESS);
        o.setOperatorId(OPERATOR_ID);
        return o;
    }

    private PackingItem buildItem(UUID packingOrderId, UUID productId, UUID lotId) {
        PackingItem item = PackingItem.builder()
                .tenantId(TENANT_ID)
                .packingOrderId(packingOrderId)
                .pickingItemId(PICKING_ITEM_ID)
                .productId(productId)
                .lotId(lotId)
                .quantityPacked(3)
                .scanned(false)
                .build();
        setEntityId(item, ITEM_ID);
        return item;
    }

    private ProductWms buildProduct(String sku) {
        ProductWms p = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .sku(sku)
                .name("Produto " + sku)
                .build();
        setEntityId(p, PRODUCT_ID);
        return p;
    }

    private Lot buildLot(String lotNumber) {
        Lot lot = Lot.builder()
                .tenantId(TENANT_ID)
                .lotNumber(lotNumber)
                .build();
        setEntityId(lot, LOT_ID,
                "com.odin.wms.domain.entity.base.BaseAppendOnlyEntity");
        return lot;
    }

    private PickingCompletedEvent buildPickingCompletedEvent(
            List<PickingCompletedEvent.PickingCompletedItem> items) {
        // Ordem: eventType, tenantId, pickingOrderId, crmOrderId, status, warehouseId, operatorId, items, completedAt
        return new PickingCompletedEvent(
                "PICKING_ORDER_COMPLETED",
                TENANT_ID,
                PICKING_ORDER_ID,
                CRM_ORDER_ID,
                "COMPLETED",
                WAREHOUSE_ID,
                OPERATOR_ID,
                items,
                Instant.now()
        );
    }

    private static void setEntityId(Object entity, UUID id) {
        setEntityId(entity, id, "com.odin.wms.domain.entity.base.BaseEntity");
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
