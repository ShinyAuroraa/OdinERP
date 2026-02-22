package com.odin.wms.quarantine;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.DecideQuarantineRequest;
import com.odin.wms.dto.response.QuarantineTaskResponse;
import com.odin.wms.dto.response.QuarantineTaskSummaryResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.messaging.QuarantineEventPublisher;
import com.odin.wms.service.QuarantineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuarantineServiceTest {

    @Mock private QuarantineTaskRepository quarantineTaskRepository;
    @Mock private ReceivingNoteRepository receivingNoteRepository;
    @Mock private ReceivingNoteItemRepository receivingNoteItemRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private QuarantineEventPublisher eventPublisher;

    @InjectMocks
    private QuarantineService quarantineService;

    private MockedStatic<TenantContextHolder> tenantMock;

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID NOTE_ID           = UUID.randomUUID();
    private static final UUID TASK_ID           = UUID.randomUUID();
    private static final UUID PRODUCT_ID        = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID      = UUID.randomUUID();
    private static final UUID DOCK_LOC_ID       = UUID.randomUUID();
    private static final UUID QUARANTINE_LOC_ID = UUID.randomUUID();
    private static final UUID STORAGE_LOC_ID    = UUID.randomUUID();
    private static final UUID STOCK_ITEM_ID     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantMock = mockStatic(TenantContextHolder.class);
        tenantMock.when(TenantContextHolder::getTenantId).thenReturn(TENANT_ID);

        SecurityContext ctx = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // generateTasks()
    // -------------------------------------------------------------------------

    @Test
    void generateTasks_success_createsPendingTaskAndQuarantineInMovement() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildDivergenceNoteWithFlaggedItem(dock, stockItem);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(quarantineTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(false);
        when(locationRepository.findQuarantineByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of(quarantine));
        when(quarantineTaskRepository.save(any())).thenAnswer(inv -> {
            QuarantineTask t = inv.getArgument(0);
            setUUID(t, TASK_ID);
            return t;
        });
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<QuarantineTaskResponse> result = quarantineService.generateTasks(NOTE_ID, TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(QuarantineStatus.PENDING);
        assertThat(result.getFirst().quarantineLocationId()).isEqualTo(QUARANTINE_LOC_ID);
        assertThat(result.getFirst().sourceLocationId()).isEqualTo(DOCK_LOC_ID);
        verify(stockMovementRepository).save(argThat(m -> m.getType() == MovementType.QUARANTINE_IN));
        // StockItem deve ter localização atualizada para quarentena
        assertThat(stockItem.getLocation()).isEqualTo(quarantine);
    }

    @Test
    void generateTasks_noteNotCompletedWithDivergence_throwsBusinessException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED, dock, product);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> quarantineService.generateTasks(NOTE_ID, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COMPLETED_WITH_DIVERGENCE");
    }

    @Test
    void generateTasks_duplicateCall_throwsConflictException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(quarantineTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(true);

        assertThatThrownBy(() -> quarantineService.generateTasks(NOTE_ID, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já geradas");
    }

    @Test
    void generateTasks_noQuarantineLocation_throwsBusinessException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildDivergenceNoteWithFlaggedItem(dock, stockItem);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(quarantineTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(false);
        when(locationRepository.findQuarantineByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> quarantineService.generateTasks(NOTE_ID, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("QUARANTINE");
    }

    // -------------------------------------------------------------------------
    // start()
    // -------------------------------------------------------------------------

    @Test
    void start_pendingTask_transitionsToInReview() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.PENDING, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(quarantineTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuarantineTaskResponse response = quarantineService.start(TASK_ID, TENANT_ID);

        assertThat(response.status()).isEqualTo(QuarantineStatus.IN_REVIEW);
        assertThat(response.startedAt()).isNotNull();
    }

    @Test
    void start_notPendingTask_throwsConflictException() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.IN_REVIEW, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> quarantineService.start(TASK_ID, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("IN_REVIEW");
    }

    // -------------------------------------------------------------------------
    // decide() — three paths
    // -------------------------------------------------------------------------

    @Test
    void decide_releaseToStock_success_createsMovementAndAuditLog() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        Location storage = buildStorageLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.IN_REVIEW, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(locationRepository.findWarehouseIdByLocationId(QUARANTINE_LOC_ID)).thenReturn(Optional.of(WAREHOUSE_ID));
        when(stockItemRepository.countByTenantIdGroupByLocationId(TENANT_ID)).thenReturn(List.of());
        when(locationRepository.findStorageOrPickingByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of(storage));
        when(stockItemRepository.findLocationIdsWithProduct(TENANT_ID, PRODUCT_ID)).thenReturn(List.of());
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quarantineTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DecideQuarantineRequest request = new DecideQuarantineRequest(QuarantineDecision.RELEASE_TO_STOCK, "OK");
        QuarantineTaskResponse response = quarantineService.decide(TASK_ID, request, TENANT_ID);

        assertThat(response.status()).isEqualTo(QuarantineStatus.APPROVED);
        assertThat(response.decision()).isEqualTo(QuarantineDecision.RELEASE_TO_STOCK);
        assertThat(response.decidedAt()).isNotNull();
        verify(stockMovementRepository).save(argThat(m -> m.getType() == MovementType.PUTAWAY));
        verify(auditLogRepository).save(any());
        assertThat(stockItem.getLocation()).isEqualTo(storage);
    }

    @Test
    void decide_returnToSupplier_success_publishesKafkaEvent() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.IN_REVIEW, dock, quarantine, stockItem, note);
        int originalQty = stockItem.getQuantityAvailable();

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quarantineTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DecideQuarantineRequest request = new DecideQuarantineRequest(
                QuarantineDecision.RETURN_TO_SUPPLIER, "Produto com defeito");
        QuarantineTaskResponse response = quarantineService.decide(TASK_ID, request, TENANT_ID);

        assertThat(response.status()).isEqualTo(QuarantineStatus.REJECTED);
        assertThat(response.decision()).isEqualTo(QuarantineDecision.RETURN_TO_SUPPLIER);
        // quantityAvailable deve permanecer inalterado
        assertThat(stockItem.getQuantityAvailable()).isEqualTo(originalQty);
        verify(stockMovementRepository).save(argThat(m -> m.getType() == MovementType.QUARANTINE_OUT));
        verify(eventPublisher).publishReturnToSupplier(task);
    }

    @Test
    void decide_scrap_success_zerosQuantityAvailable() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.IN_REVIEW, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quarantineTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DecideQuarantineRequest request = new DecideQuarantineRequest(QuarantineDecision.SCRAP, "Descarte total");
        QuarantineTaskResponse response = quarantineService.decide(TASK_ID, request, TENANT_ID);

        assertThat(response.status()).isEqualTo(QuarantineStatus.REJECTED);
        assertThat(response.decision()).isEqualTo(QuarantineDecision.SCRAP);
        assertThat(stockItem.getQuantityAvailable()).isEqualTo(0);
        verify(stockMovementRepository).save(argThat(m ->
                m.getType() == MovementType.QUARANTINE_OUT && m.getQuantity() < 0));
    }

    @Test
    void decide_notInReview_throwsConflictException() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.PENDING, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        DecideQuarantineRequest request = new DecideQuarantineRequest(QuarantineDecision.RELEASE_TO_STOCK, null);
        assertThatThrownBy(() -> quarantineService.decide(TASK_ID, request, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("IN_REVIEW");
    }

    // -------------------------------------------------------------------------
    // cancel()
    // -------------------------------------------------------------------------

    @Test
    void cancel_pendingTask_success_noAdditionalMovement() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.PENDING, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(quarantineTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        quarantineService.cancel(TASK_ID, TENANT_ID);

        assertThat(task.getStatus()).isEqualTo(QuarantineStatus.CANCELLED);
        assertThat(task.getCancelledAt()).isNotNull();
        verify(stockMovementRepository, never()).save(any());
        verify(stockItemRepository, never()).save(any());
    }

    @Test
    void cancel_approvedTask_throwsConflictException() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.APPROVED, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> quarantineService.cancel(TASK_ID, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cancelada");
    }

    // -------------------------------------------------------------------------
    // findById() — not found
    // -------------------------------------------------------------------------

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quarantineService.findById(TASK_ID, TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(TASK_ID.toString());
    }

    // -------------------------------------------------------------------------
    // findAll() — status filter
    // -------------------------------------------------------------------------

    @Test
    void findAll_withStatusFilter_returnsSummaryList() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.PENDING, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByTenantIdAndStatus(TENANT_ID, QuarantineStatus.PENDING))
                .thenReturn(List.of(task));

        List<QuarantineTaskSummaryResponse> result =
                quarantineService.findAll(TENANT_ID, QuarantineStatus.PENDING, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(QuarantineStatus.PENDING);
        assertThat(result.getFirst().receivingNoteId()).isEqualTo(NOTE_ID);
    }

    // -------------------------------------------------------------------------
    // cancel() — IN_REVIEW branch
    // -------------------------------------------------------------------------

    @Test
    void cancel_inReviewTask_success_noAdditionalMovement() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.IN_REVIEW, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(quarantineTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        quarantineService.cancel(TASK_ID, TENANT_ID);

        assertThat(task.getStatus()).isEqualTo(QuarantineStatus.CANCELLED);
        assertThat(task.getCancelledAt()).isNotNull();
        verify(stockMovementRepository, never()).save(any());
        verify(stockItemRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // generateTasks() — sem itens FLAGGED
    // -------------------------------------------------------------------------

    @Test
    void generateTasks_noFlaggedItems_throwsBusinessException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false);
        Location quarantine = buildQuarantineLocation();
        // Nota COMPLETED_WITH_DIVERGENCE mas com item CONFIRMED (não FLAGGED)
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        ReceivingNoteItem confirmedItem = ReceivingNoteItem.builder()
                .tenantId(TENANT_ID)
                .receivingNote(note)
                .product(product)
                .expectedQuantity(10)
                .receivedQuantity(8)
                .itemStatus(ReceivingItemStatus.CONFIRMED)
                .build();
        setUUID(confirmedItem, UUID.randomUUID());
        note.getItems().add(confirmedItem);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(quarantineTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(false);
        when(locationRepository.findQuarantineByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of(quarantine));

        assertThatThrownBy(() -> quarantineService.generateTasks(NOTE_ID, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FLAGGED");
    }

    // -------------------------------------------------------------------------
    // decide() — RELEASE_TO_STOCK sem location STORAGE disponível
    // -------------------------------------------------------------------------

    @Test
    void decide_releaseToStock_noStorageAvailable_throwsBusinessException() {
        Location dock = buildDockLocation();
        Location quarantine = buildQuarantineLocation();
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(quarantine, product);
        ReceivingNote note = buildNoteWithStatus(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        QuarantineTask task = buildTask(QuarantineStatus.IN_REVIEW, dock, quarantine, stockItem, note);

        when(quarantineTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(locationRepository.findWarehouseIdByLocationId(QUARANTINE_LOC_ID))
                .thenReturn(Optional.of(WAREHOUSE_ID));
        when(stockItemRepository.countByTenantIdGroupByLocationId(TENANT_ID)).thenReturn(List.of());
        when(locationRepository.findStorageOrPickingByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of());
        when(stockItemRepository.findLocationIdsWithProduct(TENANT_ID, PRODUCT_ID)).thenReturn(List.of());

        DecideQuarantineRequest request = new DecideQuarantineRequest(QuarantineDecision.RELEASE_TO_STOCK, null);
        assertThatThrownBy(() -> quarantineService.decide(TASK_ID, request, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disponível");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Location buildDockLocation() {
        Warehouse warehouse = Warehouse.builder().tenantId(TENANT_ID).code("WH-01").name("WH01").build();
        setUUID(warehouse, WAREHOUSE_ID);

        Zone zone = Zone.builder().tenantId(TENANT_ID).code("Z1").name("Zona 1").warehouse(warehouse).build();
        Aisle aisle = Aisle.builder().tenantId(TENANT_ID).code("A1").zone(zone).build();
        Shelf shelf = Shelf.builder().tenantId(TENANT_ID).code("S1").aisle(aisle).build();

        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .shelf(shelf)
                .code("DOCK-01")
                .fullAddress("WH01.Z1.A1.S1.DOCK-01")
                .type(LocationType.RECEIVING_DOCK)
                .active(true)
                .build();
        setUUID(loc, DOCK_LOC_ID);
        return loc;
    }

    private Location buildQuarantineLocation() {
        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .code("QUAR-01")
                .fullAddress("WH01.Z1.A1.S1.QUAR-01")
                .type(LocationType.QUARANTINE)
                .active(true)
                .build();
        setUUID(loc, QUARANTINE_LOC_ID);
        return loc;
    }

    private Location buildStorageLocation() {
        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .code("STOR-01")
                .fullAddress("WH01.Z1.A1.S1.STOR-01")
                .type(LocationType.STORAGE)
                .active(true)
                .build();
        setUUID(loc, STORAGE_LOC_ID);
        return loc;
    }

    private ProductWms buildProduct(boolean controlsExpiry) {
        ProductWms p = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .sku("SKU-001")
                .name("Produto Teste")
                .storageType(StorageType.DRY)
                .controlsLot(false)
                .controlsSerial(false)
                .controlsExpiry(controlsExpiry)
                .active(true)
                .build();
        setUUID(p, PRODUCT_ID);
        return p;
    }

    private StockItem buildStockItem(Location location, ProductWms product) {
        StockItem item = StockItem.builder()
                .tenantId(TENANT_ID)
                .location(location)
                .product(product)
                .quantityAvailable(10)
                .build();
        setUUID(item, STOCK_ITEM_ID);
        return item;
    }

    private ReceivingNote buildNoteWithStatus(ReceivingStatus status, Location dock, ProductWms product) {
        Warehouse warehouse = dock.getShelf().getAisle().getZone().getWarehouse();
        ReceivingNote note = ReceivingNote.builder()
                .tenantId(TENANT_ID)
                .warehouse(warehouse)
                .dockLocation(dock)
                .status(status)
                .items(new ArrayList<>())
                .build();
        setUUID(note, NOTE_ID);
        return note;
    }

    private ReceivingNote buildDivergenceNoteWithFlaggedItem(Location dock, StockItem stockItem) {
        Warehouse warehouse = dock.getShelf().getAisle().getZone().getWarehouse();
        ReceivingNote note = ReceivingNote.builder()
                .tenantId(TENANT_ID)
                .warehouse(warehouse)
                .dockLocation(dock)
                .status(ReceivingStatus.COMPLETED_WITH_DIVERGENCE)
                .items(new ArrayList<>())
                .build();
        setUUID(note, NOTE_ID);

        ReceivingNoteItem item = ReceivingNoteItem.builder()
                .tenantId(TENANT_ID)
                .receivingNote(note)
                .product(stockItem.getProduct())
                .expectedQuantity(10)
                .receivedQuantity(8)
                .itemStatus(ReceivingItemStatus.FLAGGED)
                .divergenceType(DivergenceType.QUALITY)
                .build();
        item.setStockItem(stockItem);
        setUUID(item, UUID.randomUUID());
        note.getItems().add(item);
        return note;
    }

    private QuarantineTask buildTask(QuarantineStatus status, Location sourceLocation,
                                      Location quarantineLocation, StockItem stockItem,
                                      ReceivingNote note) {
        ReceivingNoteItem item;
        if (note.getItems().isEmpty()) {
            // toResponse() exige receivingNoteItem não-nulo para chamar .getId()
            item = ReceivingNoteItem.builder()
                    .tenantId(TENANT_ID)
                    .receivingNote(note)
                    .product(stockItem.getProduct())
                    .expectedQuantity(10)
                    .receivedQuantity(10)
                    .itemStatus(ReceivingItemStatus.FLAGGED)
                    .divergenceType(DivergenceType.QUALITY)
                    .build();
            setUUID(item, UUID.randomUUID());
        } else {
            item = note.getItems().get(0);
        }

        QuarantineTask task = QuarantineTask.builder()
                .tenantId(TENANT_ID)
                .receivingNote(note)
                .receivingNoteItem(item)
                .stockItem(stockItem)
                .sourceLocation(sourceLocation)
                .quarantineLocation(quarantineLocation)
                .status(status)
                .build();
        setUUID(task, TASK_ID);
        return task;
    }

    private void setUUID(Object entity, UUID id) {
        try {
            Class<?> base = Class.forName("com.odin.wms.domain.entity.base.BaseEntity");
            var field = base.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID via reflection", e);
        }
    }
}
