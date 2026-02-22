package com.odin.wms.stock;

import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.response.StockBalanceResponse;
import com.odin.wms.dto.response.WarehouseOccupationResponse;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.service.StockBalanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockBalanceServiceTest {

    @Mock private StockItemRepository stockItemRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ZoneRepository zoneRepository;

    @InjectMocks
    private StockBalanceService stockBalanceService;

    private static final UUID TENANT_ID    = UUID.randomUUID();
    private static final UUID PRODUCT_ID   = UUID.randomUUID();
    private static final UUID LOCATION_ID  = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID ZONE_ID      = UUID.randomUUID();
    private static final UUID ITEM_ID      = UUID.randomUUID();

    // -------------------------------------------------------------------------
    // getBalance — AC11 cenários 1-4
    // -------------------------------------------------------------------------

    @Test
    void getBalance_noFilters_returnsAllTenantItems() {
        StockItem item = buildStockItem(ITEM_ID, null);
        when(stockItemRepository.findByFilters(TENANT_ID, null, null, null, null))
                .thenReturn(List.of(item));

        List<StockBalanceResponse> result = stockBalanceService.getBalance(
                TENANT_ID, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().productId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getFirst().productCode()).isEqualTo("SKU-001");
        assertThat(result.getFirst().quantityAvailable()).isEqualTo(10);
        assertThat(result.getFirst().lotId()).isNull();
    }

    @Test
    void getBalance_withProductFilter_returnsFilteredItems() {
        StockItem item = buildStockItem(ITEM_ID, null);
        when(stockItemRepository.findByFilters(TENANT_ID, PRODUCT_ID, null, null, null))
                .thenReturn(List.of(item));

        List<StockBalanceResponse> result = stockBalanceService.getBalance(
                TENANT_ID, PRODUCT_ID, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().productId()).isEqualTo(PRODUCT_ID);
    }

    @Test
    void getBalance_withLocationFilter_returnsFilteredItems() {
        StockItem item = buildStockItem(ITEM_ID, null);
        when(stockItemRepository.findByFilters(TENANT_ID, null, LOCATION_ID, null, null))
                .thenReturn(List.of(item));

        List<StockBalanceResponse> result = stockBalanceService.getBalance(
                TENANT_ID, null, LOCATION_ID, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().locationId()).isEqualTo(LOCATION_ID);
    }

    @Test
    void getBalance_withWarehouseId_returnsItemsInWarehouse() {
        StockItem item1 = buildStockItem(UUID.randomUUID(), null);
        StockItem item2 = buildStockItem(UUID.randomUUID(), null);
        when(stockItemRepository.findByFilters(TENANT_ID, null, null, null, WAREHOUSE_ID))
                .thenReturn(List.of(item1, item2));

        List<StockBalanceResponse> result = stockBalanceService.getBalance(
                TENANT_ID, null, null, null, WAREHOUSE_ID);

        assertThat(result).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // getLocationBalance — AC11 cenários 5-6
    // -------------------------------------------------------------------------

    @Test
    void getLocationBalance_validLocation_returnsList() {
        Location loc = buildLocation(LOCATION_ID);
        StockItem item = buildStockItem(ITEM_ID, null);

        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(loc));
        when(stockItemRepository.findByFilters(TENANT_ID, null, LOCATION_ID, null, null))
                .thenReturn(List.of(item));

        List<StockBalanceResponse> result = stockBalanceService.getLocationBalance(TENANT_ID, LOCATION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().locationId()).isEqualTo(LOCATION_ID);
        assertThat(result.getFirst().locationCode()).isEqualTo("STOR-01");
    }

    @Test
    void getLocationBalance_unknownLocation_throwsNotFoundException() {
        when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockBalanceService.getLocationBalance(TENANT_ID, LOCATION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(LOCATION_ID.toString());
    }

    // -------------------------------------------------------------------------
    // getOccupation — AC11 cenários 7-9
    // -------------------------------------------------------------------------

    @Test
    void getOccupation_validWarehouse_returnsOccupationResponse() {
        Warehouse warehouse = buildWarehouse();
        Zone zone = buildZone(warehouse);

        // 10 locations total, 6 occupied; 100 total capacity, 60 used
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));
        when(zoneRepository.findByTenantIdAndWarehouseId(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(zone));
        when(locationRepository.countLocationsByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(rowList(ZONE_ID, 10L));
        when(stockItemRepository.countOccupiedLocationsByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(rowList(ZONE_ID, 6L));
        when(locationRepository.sumCapacityUnitsByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(rowList(ZONE_ID, 100L));
        when(stockItemRepository.sumQuantitiesByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(rowList(ZONE_ID, 60L));

        WarehouseOccupationResponse result = stockBalanceService.getOccupation(TENANT_ID, WAREHOUSE_ID);

        assertThat(result.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(result.warehouseName()).isEqualTo("Armazém Principal");
        assertThat(result.totalLocations()).isEqualTo(10L);
        assertThat(result.occupiedLocations()).isEqualTo(6L);
        assertThat(result.occupancyRate()).isEqualTo(60.0);
        assertThat(result.zones()).hasSize(1);
        assertThat(result.zones().getFirst().occupancyRate()).isEqualTo(60.0);
        assertThat(result.zones().getFirst().totalCapacityUnits()).isEqualTo(100L);
        assertThat(result.zones().getFirst().usedCapacityUnits()).isEqualTo(60L);
    }

    @Test
    void getOccupation_unknownWarehouse_throwsNotFoundException() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockBalanceService.getOccupation(TENANT_ID, WAREHOUSE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(WAREHOUSE_ID.toString());
    }

    @Test
    void getOccupation_emptyWarehouse_returnsZeroOccupancy() {
        Warehouse warehouse = buildWarehouse();
        Zone zone = buildZone(warehouse);

        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));
        when(zoneRepository.findByTenantIdAndWarehouseId(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(List.of(zone));
        when(locationRepository.countLocationsByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(List.of());
        when(stockItemRepository.countOccupiedLocationsByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(List.of());
        when(locationRepository.sumCapacityUnitsByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(List.of());
        when(stockItemRepository.sumQuantitiesByZone(TENANT_ID, WAREHOUSE_ID))
                .thenReturn(List.of());

        WarehouseOccupationResponse result = stockBalanceService.getOccupation(TENANT_ID, WAREHOUSE_ID);

        assertThat(result.totalLocations()).isZero();
        assertThat(result.occupiedLocations()).isZero();
        assertThat(result.occupancyRate()).isEqualTo(0.0);
        assertThat(result.zones()).hasSize(1);
        assertThat(result.zones().getFirst().totalLocations()).isZero();
        assertThat(result.zones().getFirst().occupancyRate()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private StockItem buildStockItem(UUID itemId, Lot lot) {
        ProductWms product = ProductWms.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .sku("SKU-001")
                .name("Produto Teste")
                .storageType(StorageType.DRY)
                .controlsLot(false)
                .controlsSerial(false)
                .controlsExpiry(false)
                .active(true)
                .build();
        setUUID(product, PRODUCT_ID);

        Location location = buildLocation(LOCATION_ID);

        StockItem item = StockItem.builder()
                .tenantId(TENANT_ID)
                .location(location)
                .product(product)
                .lot(lot)
                .quantityAvailable(10)
                .quantityReserved(0)
                .quantityQuarantine(0)
                .quantityDamaged(0)
                .build();
        setUUID(item, itemId);
        return item;
    }

    private Location buildLocation(UUID locationId) {
        Warehouse warehouse = buildWarehouse();
        Zone zone = buildZone(warehouse);
        Aisle aisle = Aisle.builder().tenantId(TENANT_ID).code("A1").zone(zone).build();
        Shelf shelf = Shelf.builder().tenantId(TENANT_ID).code("S1").aisle(aisle).build();

        Location loc = Location.builder()
                .tenantId(TENANT_ID)
                .shelf(shelf)
                .code("STOR-01")
                .fullAddress("WH01.Z1.A1.S1.STOR-01")
                .type(LocationType.STORAGE)
                .capacityUnits(10)
                .active(true)
                .build();
        setUUID(loc, locationId);
        return loc;
    }

    private Warehouse buildWarehouse() {
        Warehouse wh = Warehouse.builder()
                .tenantId(TENANT_ID)
                .code("WH-01")
                .name("Armazém Principal")
                .build();
        setUUID(wh, WAREHOUSE_ID);
        return wh;
    }

    private Zone buildZone(Warehouse warehouse) {
        Zone zone = Zone.builder()
                .tenantId(TENANT_ID)
                .code("Z1")
                .name("Zona 1")
                .warehouse(warehouse)
                .build();
        setUUID(zone, ZONE_ID);
        return zone;
    }

    /** Cria List<Object[]> com uma única linha [zoneId, value] — evita ambiguidade varargs. */
    private List<Object[]> rowList(UUID zoneId, long value) {
        List<Object[]> list = new ArrayList<>();
        list.add(new Object[]{zoneId, value});
        return list;
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
}
