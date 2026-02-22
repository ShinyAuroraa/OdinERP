package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.CreateLocationRequest;
import com.odin.wms.dto.request.UpdateLocationRequest;
import com.odin.wms.dto.response.LocationResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock private LocationRepository locationRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private AisleRepository aisleRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private WarehouseRepository warehouseRepository;

    @InjectMocks
    private LocationService locationService;

    private MockedStatic<TenantContextHolder> tenantContextHolderMock;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SHELF_ID  = UUID.randomUUID();
    private static final UUID AISLE_ID  = UUID.randomUUID();
    private static final UUID ZONE_ID   = UUID.randomUUID();
    private static final UUID WH_ID     = UUID.randomUUID();
    private static final UUID LOC_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantContextHolderMock = mockStatic(TenantContextHolder.class);
        tenantContextHolderMock.when(TenantContextHolder::getTenantId).thenReturn(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        tenantContextHolderMock.close();
    }

    @Test
    void create_success_computesFullAddress() {
        var hierarchy = buildHierarchy();
        stubHierarchy(hierarchy);
        when(locationRepository.existsByTenantIdAndCode(TENANT_ID, "L001")).thenReturn(false);

        Location saved = Location.builder()
                .tenantId(TENANT_ID)
                .shelf(hierarchy.shelf())
                .code("L001")
                .fullAddress("WH-001/ZN-01/A01/S01/L001")
                .type(LocationType.PICKING)
                .active(true)
                .build();
        setId(saved, LOC_ID);
        when(locationRepository.save(any(Location.class))).thenReturn(saved);

        LocationResponse response = locationService.create(SHELF_ID,
                new CreateLocationRequest("L001", LocationType.PICKING, null, null));

        assertThat(response.fullAddress()).isEqualTo("WH-001/ZN-01/A01/S01/L001");
        assertThat(response.code()).isEqualTo("L001");
        assertThat(response.type()).isEqualTo(LocationType.PICKING);
    }

    @Test
    void create_duplicateCode_throwsBusinessException() {
        var hierarchy = buildHierarchy();
        when(shelfRepository.findById(SHELF_ID)).thenReturn(Optional.of(hierarchy.shelf()));
        when(locationRepository.existsByTenantIdAndCode(TENANT_ID, "L001")).thenReturn(true);

        assertThatThrownBy(() -> locationService.create(SHELF_ID,
                new CreateLocationRequest("L001", LocationType.STORAGE, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("L001");
    }

    @Test
    void create_shelfNotInTenant_throwsResourceNotFoundException() {
        Shelf otherShelf = Shelf.builder().tenantId(UUID.randomUUID()).code("S01").level(1).build();
        setId(otherShelf, SHELF_ID);
        when(shelfRepository.findById(SHELF_ID)).thenReturn(Optional.of(otherShelf));

        assertThatThrownBy(() -> locationService.create(SHELF_ID,
                new CreateLocationRequest("L001", LocationType.STORAGE, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByType_returnsOnlyTenantLocations() {
        var hierarchy = buildHierarchy();
        Location loc = Location.builder()
                .tenantId(TENANT_ID).shelf(hierarchy.shelf()).code("L002")
                .fullAddress("WH-001/ZN-01/A01/S01/L002").type(LocationType.RECEIVING_DOCK).active(true).build();
        setId(loc, LOC_ID);
        when(locationRepository.findByTenantIdAndType(TENANT_ID, LocationType.RECEIVING_DOCK))
                .thenReturn(List.of(loc));

        var results = locationService.findByType(LocationType.RECEIVING_DOCK);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(LocationType.RECEIVING_DOCK);
    }

    @Test
    void deactivate_setsActiveFalse() {
        var hierarchy = buildHierarchy();
        Location loc = Location.builder()
                .tenantId(TENANT_ID).shelf(hierarchy.shelf()).code("L003")
                .fullAddress("x").type(LocationType.STORAGE).active(true).build();
        setId(loc, LOC_ID);
        when(locationRepository.findById(LOC_ID)).thenReturn(Optional.of(loc));
        when(locationRepository.save(any())).thenReturn(loc);

        locationService.deactivate(LOC_ID);
        assertThat(loc.getActive()).isFalse();
    }

    @Test
    void update_doesNotChangeCodeOrFullAddress() {
        var hierarchy = buildHierarchy();
        Location loc = Location.builder()
                .tenantId(TENANT_ID).shelf(hierarchy.shelf()).code("L004")
                .fullAddress("WH-001/ZN-01/A01/S01/L004").type(LocationType.STORAGE).active(true).build();
        setId(loc, LOC_ID);
        when(locationRepository.findById(LOC_ID)).thenReturn(Optional.of(loc));
        when(locationRepository.save(any())).thenReturn(loc);

        locationService.update(LOC_ID, new UpdateLocationRequest(LocationType.QUARANTINE, 50, null));

        assertThat(loc.getCode()).isEqualTo("L004");
        assertThat(loc.getFullAddress()).isEqualTo("WH-001/ZN-01/A01/S01/L004");
        assertThat(loc.getType()).isEqualTo(LocationType.QUARANTINE);
        assertThat(loc.getCapacityUnits()).isEqualTo(50);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Hierarchy(Warehouse warehouse, Zone zone, Aisle aisle, Shelf shelf) {}

    private Hierarchy buildHierarchy() {
        Warehouse wh = Warehouse.builder().tenantId(TENANT_ID).code("WH-001").name("WH").active(true).build();
        setId(wh, WH_ID);

        Zone zone = Zone.builder().tenantId(TENANT_ID).warehouse(wh).code("ZN-01").name("ZN")
                .type(LocationType.STORAGE).active(true).build();
        setId(zone, ZONE_ID);

        Aisle aisle = Aisle.builder().tenantId(TENANT_ID).zone(zone).code("A01").build();
        setId(aisle, AISLE_ID);

        Shelf shelf = Shelf.builder().tenantId(TENANT_ID).aisle(aisle).code("S01").level(1).build();
        setId(shelf, SHELF_ID);

        return new Hierarchy(wh, zone, aisle, shelf);
    }

    private void stubHierarchy(Hierarchy h) {
        when(shelfRepository.findById(SHELF_ID)).thenReturn(Optional.of(h.shelf()));
        when(aisleRepository.findById(AISLE_ID)).thenReturn(Optional.of(h.aisle()));
        when(zoneRepository.findById(ZONE_ID)).thenReturn(Optional.of(h.zone()));
        when(warehouseRepository.findById(WH_ID)).thenReturn(Optional.of(h.warehouse()));
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = com.odin.wms.domain.entity.base.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
