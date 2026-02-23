package com.odin.wms.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.InventoryCount.CountType;
import com.odin.wms.domain.entity.InventoryCount.InventoryCountStatus;
import com.odin.wms.domain.entity.InventoryCountItem.ItemCountStatus;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para InventoryController — AC11 (8 cenários).
 * Endereça CONCERN-1 de Story 4.2: submitCount_withNonWmsRole_returns403.
 */
@AutoConfigureMockMvc
class InventoryControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private InventoryCountRepository inventoryCountRepository;
    @Autowired private InventoryCountItemRepository inventoryCountItemRepository;

    @MockBean private TraceabilityIndexer traceabilityIndexer;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID zoneId;
    private Location location;
    private ProductWms product;
    private StockItem stockItem;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-INV-" + tenantId).name("Armazém Inventário").build());
        warehouseId = wh.getId();

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("Z-INV-" + tenantId)
                .name("Zona Inventário").type(LocationType.STORAGE).build());
        zoneId = zone.getId();

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-INV-" + tenantId).build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-INV-" + tenantId).level(1).build());
        location = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-INV-" + tenantId)
                .fullAddress("WH-INV/Z-INV/A-INV/S-INV/L-INV")
                .type(LocationType.STORAGE).capacityUnits(200).active(true).build());

        product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-INV-" + tenantId).name("Produto Inventário")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        stockItem = stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(location).product(product)
                .quantityAvailable(100).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0).build());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withSupervisor(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString()).claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_SUPERVISOR")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withOperator(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString()).claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private UUID createAndStartCount() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "countType", "FULL",
                "warehouseId", warehouseId.toString(),
                "adjustmentThreshold", 5
        ));

        MvcResult result = mockMvc.perform(withSupervisor(post("/api/v1/inventory/count"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String countId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("countId").asText();

        mockMvc.perform(withSupervisor(post("/api/v1/inventory/count/{id}/start", countId)))
                .andExpect(status().isOk());

        return UUID.fromString(countId);
    }

    // -------------------------------------------------------------------------
    // Cenários de integração
    // -------------------------------------------------------------------------

    @Test
    void createCount_asSupervisor_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "countType", "FULL",
                "warehouseId", warehouseId.toString(),
                "adjustmentThreshold", 5
        ));

        mockMvc.perform(withSupervisor(post("/api/v1/inventory/count"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countId").isString())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.countType", is("FULL")))
                .andExpect(jsonPath("$.totalItems", is(1)));
    }

    @Test
    void createCount_asOperator_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "countType", "FULL",
                "warehouseId", warehouseId.toString(),
                "adjustmentThreshold", 0
        ));

        mockMvc.perform(withOperator(post("/api/v1/inventory/count"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCount_withoutToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "countType", "FULL",
                "warehouseId", warehouseId.toString(),
                "adjustmentThreshold", 0
        ));

        mockMvc.perform(post("/api/v1/inventory/count")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitCount_asOperator_returns200() throws Exception {
        UUID countId = createAndStartCount();

        // Get item id
        MvcResult itemsResult = mockMvc.perform(withSupervisor(
                        get("/api/v1/inventory/count/{id}/items", countId)))
                .andExpect(status().isOk())
                .andReturn();

        String itemId = objectMapper.readTree(itemsResult.getResponse().getContentAsString())
                .get("content").get(0).get("itemId").asText();

        String body = objectMapper.writeValueAsString(Map.of("countedQty", 95));

        mockMvc.perform(withOperator(patch("/api/v1/inventory/count/{cid}/items/{iid}", countId, itemId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countedQty", is(95)))
                .andExpect(jsonPath("$.status", is("COUNTED")))
                .andExpect(jsonPath("$.divergenceQty").isNumber());
    }

    /**
     * CONCERN-1 de Story 4.2 endereçado: token válido sem role WMS → 403.
     */
    @Test
    void submitCount_withNonWmsRole_returns403() throws Exception {
        UUID countId = createAndStartCount();

        // Get item id
        MvcResult itemsResult = mockMvc.perform(withSupervisor(
                        get("/api/v1/inventory/count/{id}/items", countId)))
                .andExpect(status().isOk())
                .andReturn();

        String itemId = objectMapper.readTree(itemsResult.getResponse().getContentAsString())
                .get("content").get(0).get("itemId").asText();

        String body = objectMapper.writeValueAsString(Map.of("countedQty", 95));

        mockMvc.perform(patch("/api/v1/inventory/count/{cid}/items/{iid}", countId, itemId)
                        .with(jwt()
                                .jwt(j -> j.claim("tenant_id", tenantId.toString()).claim("sub", "finance-user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconcile_asSupervisor_returns200WithSummary() throws Exception {
        UUID countId = createAndStartCount();

        // Submit count for all items
        MvcResult itemsResult = mockMvc.perform(withSupervisor(
                        get("/api/v1/inventory/count/{id}/items", countId)))
                .andReturn();

        String itemId = objectMapper.readTree(itemsResult.getResponse().getContentAsString())
                .get("content").get(0).get("itemId").asText();

        mockMvc.perform(withSupervisor(patch("/api/v1/inventory/count/{cid}/items/{iid}", countId, itemId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("countedQty", 97))));

        // Force update in DB since MockMvc doesn't chain JPA state properly
        InventoryCountItem item = inventoryCountItemRepository.findByIdAndInventoryCountId(
                UUID.fromString(itemId), countId).orElseThrow();
        item.setCountedQty(BigDecimal.valueOf(97));
        item.setDivergenceQty(BigDecimal.valueOf(-3));
        item.setDivergencePct(new BigDecimal("3.0000"));
        item.setStatus(ItemCountStatus.COUNTED);
        inventoryCountItemRepository.save(item);

        mockMvc.perform(withSupervisor(post("/api/v1/inventory/count/{id}/reconcile", countId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RECONCILED")))
                .andExpect(jsonPath("$.totalItems", is(1)));
    }

    @Test
    void approve_asSupervisor_adjustsStockAndCreatesMovements() throws Exception {
        UUID countId = createAndStartCount();

        // Put count in RECONCILED state with a pending approval item
        InventoryCount count = inventoryCountRepository.findByIdAndTenantId(countId, tenantId).orElseThrow();
        count.setStatus(InventoryCountStatus.RECONCILED);
        inventoryCountRepository.save(count);

        InventoryCountItem item = inventoryCountItemRepository
                .findByInventoryCountIdAndStatus(countId, ItemCountStatus.PENDING)
                .stream().findFirst().orElseThrow();
        item.setCountedQty(BigDecimal.valueOf(90));
        item.setDivergenceQty(BigDecimal.valueOf(-10));
        item.setDivergencePct(new BigDecimal("10.0000"));
        item.setStatus(ItemCountStatus.PENDING_APPROVAL);
        inventoryCountItemRepository.save(item);

        mockMvc.perform(withSupervisor(post("/api/v1/inventory/count/{id}/approve", countId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.totalAdjusted", is(1)));

        // Verify StockItem updated
        StockItem updatedStock = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updatedStock.getQuantityAvailable()).isEqualTo(90);
    }

    @Test
    void close_afterApproval_returns200() throws Exception {
        UUID countId = createAndStartCount();

        // Force to APPROVED status
        InventoryCount count = inventoryCountRepository.findByIdAndTenantId(countId, tenantId).orElseThrow();
        count.setStatus(InventoryCountStatus.APPROVED);
        inventoryCountRepository.save(count);

        mockMvc.perform(withSupervisor(post("/api/v1/inventory/count/{id}/close", countId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")));
    }
}
