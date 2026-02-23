package com.odin.wms.stock;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class StockBalanceControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private StockItemRepository stockItemRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MockHttpServletRequestBuilder withAdminJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString()).claim("sub", "admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }

    private MockHttpServletRequestBuilder withOperatorJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString()).claim("sub", "op"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private StockItem createStockItem(String suffix, int qty) {
        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-SB-" + suffix).name("WH " + suffix).build());
        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("ZN-" + suffix).name("Zona " + suffix)
                .type(LocationType.STORAGE).build());
        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-" + suffix).build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-" + suffix).level(1).build());
        Location loc = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf)
                .code("L-" + suffix).fullAddress("WH-SB-" + suffix + "/ZN-" + suffix + "/A-" + suffix + "/S-" + suffix + "/L-" + suffix)
                .type(LocationType.STORAGE).capacityUnits(100).active(true).build());

        ProductWms product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-" + suffix).name("Produto " + suffix)
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        return stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(loc).product(product)
                .quantityAvailable(qty).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0).build());
    }

    // -------------------------------------------------------------------------
    // AC12 — cenários 1-8
    // -------------------------------------------------------------------------

    @Test
    void getBalance_noFilters_returns200_withData() throws Exception {
        StockItem item = createStockItem("NF", 15);

        mockMvc.perform(withAdminJwt(get("/api/v1/stock/balance")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.id=='" + item.getId() + "')].quantityAvailable",
                        hasItem(15)))
                .andExpect(jsonPath("$[?(@.id=='" + item.getId() + "')].productCode",
                        hasItem("SKU-NF")));
    }

    @Test
    void getBalance_withProductFilter_returns200_filtered() throws Exception {
        StockItem item1 = createStockItem("PF1", 10);
        createStockItem("PF2", 20);

        UUID productId = item1.getProduct().getId();

        mockMvc.perform(withAdminJwt(
                get("/api/v1/stock/balance").param("productId", productId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productId", is(productId.toString())))
                .andExpect(jsonPath("$[0].quantityAvailable", is(10)));
    }

    @Test
    void getLocationBalance_validLocation_returns200() throws Exception {
        StockItem item = createStockItem("LB", 5);
        UUID locationId = item.getLocation().getId();

        mockMvc.perform(withAdminJwt(
                get("/api/v1/stock/balance/location/{locationId}", locationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].locationId", is(locationId.toString())))
                .andExpect(jsonPath("$[0].quantityAvailable", is(5)));
    }

    @Test
    void getLocationBalance_differentTenant_returns404() throws Exception {
        // Localização pertence a outro tenant — cria com tenantId diferente
        UUID otherTenant = UUID.randomUUID();
        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(otherTenant).code("WH-OT").name("WH OT").build());
        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(otherTenant).warehouse(wh).code("ZN-OT").name("Zona OT")
                .type(LocationType.STORAGE).build());
        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(otherTenant).zone(zone).code("A-OT").build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(otherTenant).aisle(aisle).code("S-OT").level(1).build());
        Location otherLoc = locationRepository.save(Location.builder()
                .tenantId(otherTenant).shelf(shelf).code("L-OT")
                .fullAddress("WH-OT/ZN-OT/A-OT/S-OT/L-OT")
                .type(LocationType.STORAGE).active(true).build());

        // JWT do tenant corrente tenta acessar localização de outro tenant
        mockMvc.perform(withAdminJwt(
                get("/api/v1/stock/balance/location/{locationId}", otherLoc.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOccupation_validWarehouse_returns200_withZones() throws Exception {
        StockItem item = createStockItem("OCC", 30);
        // Navega a hierarquia para obter warehouseId
        UUID warehouseId = item.getLocation().getShelf().getAisle().getZone().getWarehouse().getId();

        mockMvc.perform(withAdminJwt(
                get("/api/v1/stock/occupation").param("warehouseId", warehouseId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId", is(warehouseId.toString())))
                .andExpect(jsonPath("$.totalLocations", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.zones", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.zones[0].occupancyRate", greaterThanOrEqualTo(0.0)));
    }

    @Test
    void getOccupation_unknownWarehouse_returns404() throws Exception {
        UUID unknownWarehouseId = UUID.randomUUID();

        mockMvc.perform(withAdminJwt(
                get("/api/v1/stock/occupation").param("warehouseId", unknownWarehouseId.toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBalance_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/stock/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBalance_operatorRole_returns200() throws Exception {
        mockMvc.perform(withOperatorJwt(get("/api/v1/stock/balance")))
                .andExpect(status().isOk());
    }
}
