package com.odin.wms.receiving;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.ConfirmReceivingItemRequest;
import com.odin.wms.dto.request.CreateReceivingNoteItemRequest;
import com.odin.wms.dto.request.CreateReceivingNoteRequest;
import com.odin.wms.dto.response.ReceivingNoteResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ConflictException;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.service.ReceivingNoteService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceivingNoteServiceTest {

    @Mock private ReceivingNoteRepository receivingNoteRepository;
    @Mock private ReceivingNoteItemRepository receivingNoteItemRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ProductWmsRepository productWmsRepository;
    @Mock private LotRepository lotRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SerialNumberRepository serialNumberRepository;

    @InjectMocks
    private ReceivingNoteService receivingNoteService;

    private MockedStatic<TenantContextHolder> tenantMock;

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID NOTE_ID    = UUID.randomUUID();
    private static final UUID ITEM_ID    = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID  = UUID.randomUUID();
    private static final UUID DOCK_LOC_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantMock = mockStatic(TenantContextHolder.class);
        tenantMock.when(TenantContextHolder::getTenantId).thenReturn(TENANT_ID);

        // SecurityContext sem JWT → operatorId = SYSTEM_OPERATOR_ID
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
    // create()
    // -------------------------------------------------------------------------

    @Test
    void createNote_success() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false, false, false);

        when(locationRepository.findReceivingDockByIdAndWarehouse(DOCK_LOC_ID, WAREHOUSE_ID, TENANT_ID))
                .thenReturn(Optional.of(dock));
        when(productWmsRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.of(product));

        ReceivingNote saved = buildNote(ReceivingStatus.PENDING, dock, product);
        when(receivingNoteRepository.save(any())).thenReturn(saved);

        var request = new CreateReceivingNoteRequest(
                WAREHOUSE_ID, DOCK_LOC_ID, null, null,
                List.of(new CreateReceivingNoteItemRequest(PRODUCT_ID, 10)));

        ReceivingNoteResponse response = receivingNoteService.create(request, TENANT_ID);

        assertThat(response.status()).isEqualTo(ReceivingStatus.PENDING);
        assertThat(response.warehouseId()).isEqualTo(WAREHOUSE_ID);
        verify(receivingNoteRepository).save(any());
    }

    @Test
    void createNote_invalidDockLocation_throwsBusinessException() {
        when(locationRepository.findReceivingDockByIdAndWarehouse(DOCK_LOC_ID, WAREHOUSE_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        var request = new CreateReceivingNoteRequest(
                WAREHOUSE_ID, DOCK_LOC_ID, null, null,
                List.of(new CreateReceivingNoteItemRequest(PRODUCT_ID, 5)));

        assertThatThrownBy(() -> receivingNoteService.create(request, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Dock location não pertence ao warehouse");
    }

    // -------------------------------------------------------------------------
    // confirmItem()
    // -------------------------------------------------------------------------

    @Test
    void confirmItem_success_noLot() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false, false, false);
        ReceivingNote note = buildNote(ReceivingStatus.IN_PROGRESS, dock, product);
        ReceivingNoteItem item = buildItem(note, product, 10);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID))
                .thenReturn(Optional.of(note));
        when(receivingNoteItemRepository.findByReceivingNoteIdAndId(NOTE_ID, ITEM_ID))
                .thenReturn(Optional.of(item));
        when(receivingNoteItemRepository.save(any())).thenReturn(item);

        var request = new ConfirmReceivingItemRequest(10, null, null, null, null, null);
        receivingNoteService.confirmItem(NOTE_ID, ITEM_ID, request, TENANT_ID);

        assertThat(item.getItemStatus()).isEqualTo(ReceivingItemStatus.CONFIRMED);
        assertThat(item.getDivergenceType()).isEqualTo(DivergenceType.NONE);
    }

    @Test
    void confirmItem_success_withDivergence_short() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false, false, false);
        ReceivingNote note = buildNote(ReceivingStatus.IN_PROGRESS, dock, product);
        ReceivingNoteItem item = buildItem(note, product, 10);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID))
                .thenReturn(Optional.of(note));
        when(receivingNoteItemRepository.findByReceivingNoteIdAndId(NOTE_ID, ITEM_ID))
                .thenReturn(Optional.of(item));
        when(receivingNoteItemRepository.save(any())).thenReturn(item);

        var request = new ConfirmReceivingItemRequest(7, null, null, null, null, null);
        receivingNoteService.confirmItem(NOTE_ID, ITEM_ID, request, TENANT_ID);

        assertThat(item.getDivergenceType()).isEqualTo(DivergenceType.SHORT);
        assertThat(item.getItemStatus()).isEqualTo(ReceivingItemStatus.CONFIRMED);
    }

    @Test
    void confirmItem_requiresLot_lotMissing_throwsValidationException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(true, false, false); // controlsLot = true
        ReceivingNote note = buildNote(ReceivingStatus.IN_PROGRESS, dock, product);
        ReceivingNoteItem item = buildItem(note, product, 10);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID))
                .thenReturn(Optional.of(note));
        when(receivingNoteItemRepository.findByReceivingNoteIdAndId(NOTE_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        var request = new ConfirmReceivingItemRequest(10, null, null, null, null, null);

        assertThatThrownBy(() -> receivingNoteService.confirmItem(NOTE_ID, ITEM_ID, request, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lote obrigatório");
    }

    @Test
    void confirmItem_expiredProduct_throwsValidationException() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false, false, true); // controlsExpiry = true
        ReceivingNote note = buildNote(ReceivingStatus.IN_PROGRESS, dock, product);
        ReceivingNoteItem item = buildItem(note, product, 5);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID))
                .thenReturn(Optional.of(note));
        when(receivingNoteItemRepository.findByReceivingNoteIdAndId(NOTE_ID, ITEM_ID))
                .thenReturn(Optional.of(item));

        var request = new ConfirmReceivingItemRequest(5, null, null,
                LocalDate.now().minusDays(1), null, null); // expiryDate no passado

        assertThatThrownBy(() -> receivingNoteService.confirmItem(NOTE_ID, ITEM_ID, request, TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("validade expirada");
    }

    // -------------------------------------------------------------------------
    // complete()
    // -------------------------------------------------------------------------

    @Test
    void completeNote_allConfirmed_createsStockItemsAndMovements() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false, false, false);
        ReceivingNote note = buildNote(ReceivingStatus.IN_PROGRESS, dock, product);
        ReceivingNoteItem item = buildItem(note, product, 10);
        item.setItemStatus(ReceivingItemStatus.CONFIRMED);
        item.setReceivedQuantity(10);
        note.getItems().add(item);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID))
                .thenReturn(Optional.of(note));
        when(receivingNoteItemRepository.countByReceivingNoteIdAndItemStatus(NOTE_ID, ReceivingItemStatus.PENDING))
                .thenReturn(0L);

        StockItem stockItem = StockItem.builder().tenantId(TENANT_ID)
                .location(dock).product(product).quantityAvailable(10).build();
        setUUID(stockItem, UUID.randomUUID(), StockItem.class, "com.odin.wms.domain.entity.base.BaseEntity");

        when(stockItemRepository.save(any())).thenReturn(stockItem);
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receivingNoteItemRepository.save(any())).thenReturn(item);
        when(receivingNoteRepository.save(any())).thenReturn(note);

        ReceivingNoteResponse response = receivingNoteService.complete(NOTE_ID, TENANT_ID);

        assertThat(response.status()).isEqualTo(ReceivingStatus.COMPLETED);
        verify(stockItemRepository).save(any());
        verify(stockMovementRepository).save(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    void completeNote_withFlaggedItem_statusBecomesDivergence() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false, false, false);
        ReceivingNote note = buildNote(ReceivingStatus.IN_PROGRESS, dock, product);
        ReceivingNoteItem item = buildItem(note, product, 5);
        item.setItemStatus(ReceivingItemStatus.FLAGGED);
        item.setReceivedQuantity(0);
        note.getItems().add(item);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID))
                .thenReturn(Optional.of(note));
        when(receivingNoteItemRepository.countByReceivingNoteIdAndItemStatus(NOTE_ID, ReceivingItemStatus.PENDING))
                .thenReturn(0L);
        when(receivingNoteRepository.save(any())).thenReturn(note);

        ReceivingNoteResponse response = receivingNoteService.complete(NOTE_ID, TENANT_ID);

        assertThat(response.status()).isEqualTo(ReceivingStatus.COMPLETED_WITH_DIVERGENCE);
        verify(stockItemRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // approveDivergences()
    // -------------------------------------------------------------------------

    @Test
    void approveDivergences_createsStockItemWithDamagedQty() {
        Location dock = buildDockLocation();
        ProductWms product = buildProduct(false, false, false);
        ReceivingNote note = buildNote(ReceivingStatus.COMPLETED_WITH_DIVERGENCE, dock, product);
        ReceivingNoteItem item = buildItem(note, product, 5);
        item.setItemStatus(ReceivingItemStatus.FLAGGED);
        item.setReceivedQuantity(3);
        note.getItems().add(item);

        when(receivingNoteRepository.findByIdAndTenantId(NOTE_ID, TENANT_ID))
                .thenReturn(Optional.of(note));

        StockItem stockItem = StockItem.builder().tenantId(TENANT_ID)
                .location(dock).product(product).quantityDamaged(3).build();
        setUUID(stockItem, UUID.randomUUID(), StockItem.class, "com.odin.wms.domain.entity.base.BaseEntity");

        when(stockItemRepository.save(any())).thenReturn(stockItem);
        when(stockMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receivingNoteItemRepository.save(any())).thenReturn(item);
        when(receivingNoteRepository.save(any())).thenReturn(note);

        ReceivingNoteResponse response = receivingNoteService.approveDivergences(NOTE_ID, TENANT_ID);

        assertThat(response.status()).isEqualTo(ReceivingStatus.COMPLETED);
        verify(stockItemRepository).save(any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Location buildDockLocation() {
        Zone zone = Zone.builder().tenantId(TENANT_ID).code("Z1").name("Zona 1").build();
        Warehouse warehouse = Warehouse.builder().tenantId(TENANT_ID).code("WH-01").name("WH01").build();
        setUUID(warehouse, WAREHOUSE_ID, Warehouse.class, "com.odin.wms.domain.entity.base.BaseEntity");
        zone = Zone.builder().tenantId(TENANT_ID).code("Z1").name("Zona 1").warehouse(warehouse).build();

        Aisle aisle = Aisle.builder().tenantId(TENANT_ID).code("A1").zone(zone).build();
        Shelf shelf = Shelf.builder().tenantId(TENANT_ID).code("S1").aisle(aisle).build();
        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .shelf(shelf)
                .code("DOCK-01")
                .fullAddress("DOCK-01")
                .type(LocationType.RECEIVING_DOCK)
                .build();
        setUUID(loc, DOCK_LOC_ID, Location.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return loc;
    }

    private ProductWms buildProduct(boolean lot, boolean serial, boolean expiry) {
        ProductWms p = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .sku("SKU-001")
                .name("Produto Teste")
                .storageType(StorageType.DRY)
                .controlsLot(lot)
                .controlsSerial(serial)
                .controlsExpiry(expiry)
                .active(true)
                .build();
        setUUID(p, PRODUCT_ID, ProductWms.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return p;
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

    private ReceivingNoteItem buildItem(ReceivingNote note, ProductWms product, int expected) {
        ReceivingNoteItem item = ReceivingNoteItem.builder()
                .tenantId(TENANT_ID)
                .receivingNote(note)
                .product(product)
                .expectedQuantity(expected)
                .build();
        setUUID(item, ITEM_ID, ReceivingNoteItem.class, "com.odin.wms.domain.entity.base.BaseEntity");
        return item;
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
