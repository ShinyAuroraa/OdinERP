package com.odin.wms.putaway;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.ConfirmPutawayRequest;
import com.odin.wms.dto.response.PutawayTaskResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.service.PutawayService;
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
class PutawayServiceTest {

    @Mock private PutawayTaskRepository putawayTaskRepository;
    @Mock private ReceivingNoteRepository receivingNoteRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private PutawayService putawayService;

    private MockedStatic<TenantContextHolder> tenantMock;

    private static final UUID TENANT_ID      = UUID.randomUUID();
    private static final UUID NOTE_ID        = UUID.randomUUID();
    private static final UUID TASK_ID        = UUID.randomUUID();
    private static final UUID PRODUCT_ID     = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID   = UUID.randomUUID();
    private static final UUID DOCK_LOC_ID    = UUID.randomUUID();
    private static final UUID STORAGE_LOC_ID = UUID.randomUUID();
    private static final UUID STOCK_ITEM_ID  = UUID.randomUUID();

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
    void generateTasks_success_fifo_noColocation_fallbackToFirstStorage() {
        ProductWms product = buildProduct(false); // controlsExpiry=false → FIFO
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildCompletedNoteWithItem(dock, stockItem);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(putawayTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(false);
        when(stockItemRepository.countByTenantIdGroupByLocationId(TENANT_ID)).thenReturn(List.of());
        when(locationRepository.findStorageOrPickingByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of(storage));
        when(stockItemRepository.findLocationIdsWithProduct(TENANT_ID, PRODUCT_ID)).thenReturn(List.of());
        when(putawayTaskRepository.save(any())).thenAnswer(inv -> {
            PutawayTask t = inv.getArgument(0);
            setUUID(t, TASK_ID, PutawayTask.class, "com.odin.wms.domain.entity.base.BaseEntity");
            return t;
        });

        List<PutawayTaskResponse> result = putawayService.generateTasks(NOTE_ID, TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().strategyUsed()).isEqualTo(PutawayStrategy.FIFO);
        assertThat(result.getFirst().suggestedLocationId()).isEqualTo(STORAGE_LOC_ID);
        verify(putawayTaskRepository).save(any());
    }

    @Test
    void generateTasks_success_fefo_preferColocation() {
        ProductWms product = buildProduct(true); // controlsExpiry=true → FEFO
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildCompletedNoteWithItem(dock, stockItem);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(putawayTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(false);
        when(stockItemRepository.countByTenantIdGroupByLocationId(TENANT_ID)).thenReturn(List.of());
        when(locationRepository.findStorageOrPickingByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of(storage));
        when(stockItemRepository.findLocationIdsWithProductByExpiryFEFO(TENANT_ID, PRODUCT_ID))
                .thenReturn(List.of(STORAGE_LOC_ID)); // co-localização disponível
        when(putawayTaskRepository.save(any())).thenAnswer(inv -> {
            PutawayTask t = inv.getArgument(0);
            setUUID(t, TASK_ID, PutawayTask.class, "com.odin.wms.domain.entity.base.BaseEntity");
            return t;
        });

        List<PutawayTaskResponse> result = putawayService.generateTasks(NOTE_ID, TENANT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().strategyUsed()).isEqualTo(PutawayStrategy.FEFO);
        assertThat(result.getFirst().suggestedLocationId()).isEqualTo(STORAGE_LOC_ID);
    }

    @Test
    void generateTasks_noteNotCompleted_throwsBusinessException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false);
        ReceivingNote note = buildNote(ReceivingStatus.IN_PROGRESS, dock, product);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> putawayService.generateTasks(NOTE_ID, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void generateTasks_duplicateCall_throwsConflictException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(putawayTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(true);

        assertThatThrownBy(() -> putawayService.generateTasks(NOTE_ID, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já geradas");
    }

    @Test
    void generateTasks_noStorageLocations_throwsBusinessException() {
        ProductWms product = buildProduct(false);
        Location dock = buildDockLocation();
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildCompletedNoteWithItem(dock, stockItem);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID)).thenReturn(Optional.of(note));
        when(putawayTaskRepository.existsByTenantIdAndReceivingNoteId(TENANT_ID, NOTE_ID)).thenReturn(false);
        when(stockItemRepository.countByTenantIdGroupByLocationId(TENANT_ID)).thenReturn(List.of());
        when(locationRepository.findStorageOrPickingByWarehouse(WAREHOUSE_ID, TENANT_ID)).thenReturn(List.of());
        when(stockItemRepository.findLocationIdsWithProduct(TENANT_ID, PRODUCT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> putawayService.generateTasks(NOTE_ID, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nenhuma localização de armazenagem");
    }

    // -------------------------------------------------------------------------
    // start()
    // -------------------------------------------------------------------------

    @Test
    void start_pendingTask_transitionsToInProgress() {
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);
        PutawayTask task = buildPutawayTask(PutawayStatus.PENDING, dock, storage, stockItem, note);

        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(putawayTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PutawayTaskResponse response = putawayService.start(TASK_ID, TENANT_ID);

        assertThat(response.status()).isEqualTo(PutawayStatus.IN_PROGRESS);
        assertThat(response.startedAt()).isNotNull();
    }

    @Test
    void start_notPendingTask_throwsConflictException() {
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);
        PutawayTask task = buildPutawayTask(PutawayStatus.IN_PROGRESS, dock, storage, stockItem, note);

        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> putawayService.start(TASK_ID, TENANT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    // -------------------------------------------------------------------------
    // confirm()
    // -------------------------------------------------------------------------

    @Test
    void confirm_useSuggestedLocation_createsMovementAndAuditLog() {
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);
        PutawayTask task = buildPutawayTask(PutawayStatus.IN_PROGRESS, dock, storage, stockItem, note);

        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(putawayTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PutawayTaskResponse response = putawayService.confirm(TASK_ID, null, TENANT_ID);

        assertThat(response.status()).isEqualTo(PutawayStatus.CONFIRMED);
        assertThat(response.confirmedLocationId()).isEqualTo(STORAGE_LOC_ID);
        assertThat(response.confirmedAt()).isNotNull();
        verify(stockMovementRepository).save(any());
        verify(auditLogRepository).save(any());
        // StockItem deve ter localização atualizada
        assertThat(stockItem.getLocation()).isEqualTo(storage);
    }

    @Test
    void confirm_overrideLocation_sameWarehouse_success() {
        UUID altStorageId = UUID.randomUUID();
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        Location altStorage = buildStorageLocation(altStorageId, null);
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);
        PutawayTask task = buildPutawayTask(PutawayStatus.IN_PROGRESS, dock, storage, stockItem, note);

        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(locationRepository.findById(altStorageId)).thenReturn(Optional.of(altStorage));
        // Ambos retornam o mesmo warehouseId → mesmo warehouse → válido
        when(locationRepository.findWarehouseIdByLocationId(DOCK_LOC_ID)).thenReturn(Optional.of(WAREHOUSE_ID));
        when(locationRepository.findWarehouseIdByLocationId(altStorageId)).thenReturn(Optional.of(WAREHOUSE_ID));
        when(stockItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(putawayTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfirmPutawayRequest request = new ConfirmPutawayRequest(altStorageId);
        PutawayTaskResponse response = putawayService.confirm(TASK_ID, request, TENANT_ID);

        assertThat(response.confirmedLocationId()).isEqualTo(altStorageId);
        assertThat(response.status()).isEqualTo(PutawayStatus.CONFIRMED);
    }

    @Test
    void confirm_overrideLocation_invalidType_throwsBusinessException() {
        UUID dockOverrideId = UUID.randomUUID();
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        Location dockOverride = buildLocationWithType(dockOverrideId, LocationType.RECEIVING_DOCK, true);
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);
        PutawayTask task = buildPutawayTask(PutawayStatus.IN_PROGRESS, dock, storage, stockItem, note);

        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(locationRepository.findById(dockOverrideId)).thenReturn(Optional.of(dockOverride));
        when(locationRepository.findWarehouseIdByLocationId(DOCK_LOC_ID)).thenReturn(Optional.of(WAREHOUSE_ID));
        when(locationRepository.findWarehouseIdByLocationId(dockOverrideId)).thenReturn(Optional.of(WAREHOUSE_ID));

        ConfirmPutawayRequest request = new ConfirmPutawayRequest(dockOverrideId);
        assertThatThrownBy(() -> putawayService.confirm(TASK_ID, request, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("STORAGE ou PICKING");
    }

    @Test
    void confirm_overrideLocation_inactive_throwsBusinessException() {
        UUID inactiveLocId = UUID.randomUUID();
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        Location inactiveLoc = buildLocationWithType(inactiveLocId, LocationType.STORAGE, false);
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);
        PutawayTask task = buildPutawayTask(PutawayStatus.IN_PROGRESS, dock, storage, stockItem, note);

        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(locationRepository.findById(inactiveLocId)).thenReturn(Optional.of(inactiveLoc));
        when(locationRepository.findWarehouseIdByLocationId(DOCK_LOC_ID)).thenReturn(Optional.of(WAREHOUSE_ID));
        when(locationRepository.findWarehouseIdByLocationId(inactiveLocId)).thenReturn(Optional.of(WAREHOUSE_ID));

        ConfirmPutawayRequest request = new ConfirmPutawayRequest(inactiveLocId);
        assertThatThrownBy(() -> putawayService.confirm(TASK_ID, request, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativa");
    }

    // -------------------------------------------------------------------------
    // cancel()
    // -------------------------------------------------------------------------

    @Test
    void cancel_pendingTask_success_noStockMovementCreated() {
        Location dock = buildDockLocation();
        Location storage = buildStorageLocation(STORAGE_LOC_ID, null);
        ProductWms product = buildProduct(false);
        StockItem stockItem = buildStockItem(dock, product);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED, dock, product);
        PutawayTask task = buildPutawayTask(PutawayStatus.PENDING, dock, storage, stockItem, note);

        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(putawayTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        putawayService.cancel(TASK_ID, TENANT_ID);

        assertThat(task.getStatus()).isEqualTo(PutawayStatus.CANCELLED);
        assertThat(task.getCancelledAt()).isNotNull();
        verify(stockMovementRepository, never()).save(any());
        verify(stockItemRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findById() — not found
    // -------------------------------------------------------------------------

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(putawayTaskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> putawayService.findById(TASK_ID, TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(TASK_ID.toString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Location buildDockLocation() {
        Warehouse warehouse = Warehouse.builder().tenantId(TENANT_ID).code("WH-01").name("WH01").build();
        setUUID(warehouse, WAREHOUSE_ID, Warehouse.class, "com.odin.wms.domain.entity.base.BaseEntity");

        Zone zone = Zone.builder().tenantId(TENANT_ID).code("Z1").name("Zona 1").warehouse(warehouse).build();
        Aisle aisle = Aisle.builder().tenantId(TENANT_ID).code("A1").zone(zone).build();
        Shelf shelf = Shelf.builder().tenantId(TENANT_ID).code("S1").aisle(aisle).build();

        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .shelf(shelf)
                .code("DOCK-01")
                .fullAddress("WH01.Z1.A1.S1.DOCK-01")
                .type(LocationType.RECEIVING_DOCK)
                .build();
        setUUID(loc, DOCK_LOC_ID, Location.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return loc;
    }

    private Location buildStorageLocation(UUID locationId, Integer capacityUnits) {
        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .code("STOR-01")
                .fullAddress("WH01.Z1.A1.S1.STOR-01")
                .type(LocationType.STORAGE)
                .capacityUnits(capacityUnits)
                .active(true)
                .build();
        setUUID(loc, locationId, Location.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return loc;
    }

    private Location buildLocationWithType(UUID locationId, LocationType type, boolean active) {
        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .code("LOC-" + locationId.toString().substring(0, 8))
                .fullAddress("WH01.Z1.A1.S1.LOC")
                .type(type)
                .active(active)
                .build();
        setUUID(loc, locationId, Location.class, "com.odin.wms.domain.entity.base.BaseEntity");
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
        setUUID(p, PRODUCT_ID, ProductWms.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return p;
    }

    private StockItem buildStockItem(Location location, ProductWms product) {
        StockItem item = StockItem.builder()
                .tenantId(TENANT_ID)
                .location(location)
                .product(product)
                .quantityAvailable(10)
                .build();
        setUUID(item, STOCK_ITEM_ID, StockItem.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return item;
    }

    private ReceivingNote buildNote(ReceivingStatus status, Location dock, ProductWms product) {
        Warehouse warehouse = dock.getShelf().getAisle().getZone().getWarehouse();
        ReceivingNote note = ReceivingNote.builder()
                .tenantId(TENANT_ID)
                .warehouse(warehouse)
                .dockLocation(dock)
                .status(status)
                .items(new ArrayList<>())
                .build();
        setUUID(note, NOTE_ID, ReceivingNote.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return note;
    }

    private ReceivingNote buildCompletedNoteWithItem(Location dock, StockItem stockItem) {
        Warehouse warehouse = dock.getShelf().getAisle().getZone().getWarehouse();
        ReceivingNote note = ReceivingNote.builder()
                .tenantId(TENANT_ID)
                .warehouse(warehouse)
                .dockLocation(dock)
                .status(ReceivingStatus.COMPLETED)
                .items(new ArrayList<>())
                .build();
        setUUID(note, NOTE_ID, ReceivingNote.class, "com.odin.wms.domain.entity.base.BaseEntity");

        // Cria item com StockItem vinculado (simula nota já concluída)
        ReceivingNoteItem item = ReceivingNoteItem.builder()
                .tenantId(TENANT_ID)
                .receivingNote(note)
                .product(stockItem.getProduct())
                .expectedQuantity(10)
                .receivedQuantity(10)
                .itemStatus(ReceivingItemStatus.CONFIRMED)
                .build();
        item.setStockItem(stockItem);
        setUUID(item, UUID.randomUUID(), ReceivingNoteItem.class,
                "com.odin.wms.domain.entity.base.BaseEntity");
        note.getItems().add(item);
        return note;
    }

    private PutawayTask buildPutawayTask(PutawayStatus status, Location sourceLocation,
                                          Location suggestedLocation, StockItem stockItem,
                                          ReceivingNote note) {
        PutawayTask task = PutawayTask.builder()
                .tenantId(TENANT_ID)
                .receivingNote(note)
                .stockItem(stockItem)
                .sourceLocation(sourceLocation)
                .suggestedLocation(suggestedLocation)
                .strategyUsed(PutawayStrategy.FIFO)
                .status(status)
                .build();
        setUUID(task, TASK_ID, PutawayTask.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return task;
    }

    private void setUUID(Object entity, UUID id, Class<?> entityClass, String baseClassName) {
        try {
            Class<?> base = Class.forName(baseClassName);
            var field = base.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID via reflection", e);
        }
    }
}
