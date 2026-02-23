package com.odin.wms.picking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.PickingCompletedEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para PickingController — I1–I14.
 */
@AutoConfigureMockMvc
class PickingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private PickingOrderRepository pickingOrderRepository;
    @Autowired private PickingItemRepository pickingItemRepository;

    @MockBean private PickingCompletedEventPublisher pickingCompletedEventPublisher;
    @MockBean private AuditLogIndexer auditLogIndexer;

    private UUID tenantId;
    private UUID warehouseId;

    // Aisle 1 (A-01 = ímpar → DESC), Aisle 2 (A-02 = par → ASC)
    private Location locAisle1Shelf1; // A-01 / S-01
    private Location locAisle1Shelf2; // A-01 / S-02
    private Location locAisle2Shelf1; // A-02 / S-01
    private Location locAisle2Shelf2; // A-02 / S-02

    private ProductWms product1; // em A-01/S-01
    private ProductWms product2; // em A-02/S-01
    private StockItem stockItem1;
    private StockItem stockItem2;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        // Warehouse
        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-PK-" + tenantId).name("WH Picking Test").build());
        warehouseId = wh.getId();

        // Zone
        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("Z-PK-" + tenantId)
                .name("Zona Picking").type(LocationType.STORAGE).build());

        // Aisle 1 (A-01, ímpar → DESC na rota S-shape)
        Aisle aisle1 = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-01").build());
        // Aisle 2 (A-02, par → ASC na rota S-shape)
        Aisle aisle2 = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-02").build());

        // Shelves em cada corredor
        Shelf shelf1a = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle1).code("S-01").level(1).build());
        Shelf shelf1b = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle1).code("S-02").level(2).build());
        Shelf shelf2a = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle2).code("S-01").level(1).build());
        Shelf shelf2b = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle2).code("S-02").level(2).build());

        // Locations
        locAisle1Shelf1 = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf1a).code("L-A01-S01-" + tenantId)
                .fullAddress("WH/Z/A-01/S-01/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());
        locAisle1Shelf2 = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf1b).code("L-A01-S02-" + tenantId)
                .fullAddress("WH/Z/A-01/S-02/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());
        locAisle2Shelf1 = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf2a).code("L-A02-S01-" + tenantId)
                .fullAddress("WH/Z/A-02/S-01/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());
        locAisle2Shelf2 = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf2b).code("L-A02-S02-" + tenantId)
                .fullAddress("WH/Z/A-02/S-02/L1").type(LocationType.STORAGE)
                .capacityUnits(100).active(true).build());

        // Products
        product1 = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-P1-" + tenantId).name("Produto 1")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());
        product2 = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-P2-" + tenantId).name("Produto 2")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        // Stock items
        stockItem1 = stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(locAisle1Shelf1).product(product1)
                .quantityAvailable(50).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0)
                .receivedAt(Instant.now()).build());
        stockItem2 = stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(locAisle2Shelf1).product(product2)
                .quantityAvailable(50).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0)
                .receivedAt(Instant.now()).build());
    }

    // -------------------------------------------------------------------------
    // I1 — createPickingOrder_asSupervisor_returns201WithItems
    // -------------------------------------------------------------------------

    @Test
    void createPickingOrder_asSupervisor_returns201WithItems() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "warehouseId", warehouseId.toString(),
                "priority", 1,
                "items", List.of(
                        Map.of("productId", product1.getId().toString(), "quantity", 5),
                        Map.of("productId", product2.getId().toString(), "quantity", 3)
                )
        ));

        mockMvc.perform(withSupervisor(post("/api/v1/picking-orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    // -------------------------------------------------------------------------
    // I2 — createPickingOrder_asOperator_returns403
    // -------------------------------------------------------------------------

    @Test
    void createPickingOrder_asOperator_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "warehouseId", warehouseId.toString(),
                "priority", 0,
                "items", List.of(Map.of("productId", product1.getId().toString(), "quantity", 1))
        ));

        mockMvc.perform(withOperator(post("/api/v1/picking-orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I3 — assignOrder_asOperator_returns200AndReservesStockInDB
    // -------------------------------------------------------------------------

    @Test
    void assignOrder_asOperator_returns200AndReservesStockInDB() throws Exception {
        UUID orderId = createPendingOrder(5, 0);
        UUID operatorId = UUID.randomUUID();

        String assignBody = objectMapper.writeValueAsString(Map.of(
                "operatorId", operatorId.toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.operatorId").value(operatorId.toString()));

        // Verifica reserva no banco
        StockItem updated = stockItemRepository.findById(stockItem1.getId()).orElseThrow();
        assertThat(updated.getQuantityReserved()).isEqualTo(5);
        assertThat(updated.getQuantityAvailable()).isEqualTo(45); // 50 - 5
    }

    // -------------------------------------------------------------------------
    // I4 — assignOrder_insufficientStock_returns400
    // -------------------------------------------------------------------------

    @Test
    void assignOrder_insufficientStock_returns400() throws Exception {
        // Solicita mais que o disponível
        UUID orderId = createPendingOrder(100, 0);

        String assignBody = objectMapper.writeValueAsString(Map.of(
                "operatorId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // I5 — assignOrder_appliesSShapeRouting_correctSortOrder
    // -------------------------------------------------------------------------

    @Test
    void assignOrder_appliesSShapeRouting_correctSortOrder() throws Exception {
        // Cria ordem com product1 (A-01) e product2 (A-02)
        String body = objectMapper.writeValueAsString(Map.of(
                "warehouseId", warehouseId.toString(),
                "priority", 0,
                "items", List.of(
                        Map.of("productId", product1.getId().toString(), "quantity", 1),
                        Map.of("productId", product2.getId().toString(), "quantity", 1)
                )
        ));

        String createResult = mockMvc.perform(withSupervisor(post("/api/v1/picking-orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        String assignBody = objectMapper.writeValueAsString(Map.of(
                "operatorId", UUID.randomUUID().toString()
        ));

        String assignResult = mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // A-01 (ímpar) → processado primeiro → menor sort_order
        // A-02 (par) → processado segundo → maior sort_order
        var root = objectMapper.readTree(assignResult);
        var items = root.get("items");
        assertThat(items).isNotNull();
        assertThat(items.size()).isEqualTo(2);

        // Encontra sort_order do item no A-01 e A-02
        int sortOrderA01 = -1, sortOrderA02 = -1;
        for (var item : items) {
            UUID locId = UUID.fromString(item.get("locationId").asText());
            int so = item.get("sortOrder").asInt();
            if (locId.equals(locAisle1Shelf1.getId())) sortOrderA01 = so;
            if (locId.equals(locAisle2Shelf1.getId())) sortOrderA02 = so;
        }
        assertThat(sortOrderA01).isLessThan(sortOrderA02);
    }

    // -------------------------------------------------------------------------
    // I6 — pickItem_fullQuantity_asOperator_returns200
    // -------------------------------------------------------------------------

    @Test
    void pickItem_fullQuantity_asOperator_returns200() throws Exception {
        UUID orderId = createPendingOrder(5, 0);
        UUID operatorId = UUID.randomUUID();

        // Assign
        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorId", operatorId.toString()))))
                .andExpect(status().isOk());

        // Pega o itemId
        String getResult = mockMvc.perform(withOperator(get("/api/v1/picking-orders/{id}", orderId)))
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(
                objectMapper.readTree(getResult).get("items").get(0).get("id").asText());

        // Pick full quantity
        String pickBody = objectMapper.writeValueAsString(Map.of(
                "quantityPicked", 5,
                "pickedBy", operatorId.toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/pick-item/{itemId}", orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pickBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED"))
                .andExpect(jsonPath("$.quantityPicked").value(5));
    }

    // -------------------------------------------------------------------------
    // I7 — pickItem_partialQuantity_returns200WithPartialStatus
    // -------------------------------------------------------------------------

    @Test
    void pickItem_partialQuantity_returns200WithPartialStatus() throws Exception {
        UUID orderId = createPendingOrder(10, 0);

        // Assign
        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());

        String getResult = mockMvc.perform(withOperator(get("/api/v1/picking-orders/{id}", orderId)))
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(
                objectMapper.readTree(getResult).get("items").get(0).get("id").asText());

        String pickBody = objectMapper.writeValueAsString(Map.of(
                "quantityPicked", 6,
                "pickedBy", UUID.randomUUID().toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/pick-item/{itemId}", orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pickBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.quantityPicked").value(6));
    }

    // -------------------------------------------------------------------------
    // I8 — substituteLocation_validAlternative_returns200
    // -------------------------------------------------------------------------

    @Test
    void substituteLocation_validAlternative_returns200() throws Exception {
        // Adiciona stock na localização alternativa (locAisle1Shelf2)
        stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(locAisle1Shelf2).product(product1)
                .quantityAvailable(20).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0)
                .receivedAt(Instant.now()).build());

        UUID orderId = createPendingOrder(5, 0);

        // Assign
        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());

        // Pega itemId
        String getResult = mockMvc.perform(withOperator(get("/api/v1/picking-orders/{id}", orderId)))
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(
                objectMapper.readTree(getResult).get("items").get(0).get("id").asText());

        // Substitute location
        String subBody = objectMapper.writeValueAsString(Map.of(
                "alternativeLocationId", locAisle1Shelf2.getId().toString(),
                "quantityPicked", 5,
                "pickedBy", UUID.randomUUID().toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/pick-item/{itemId}/substitute-location",
                orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(locAisle1Shelf2.getId().toString()))
                .andExpect(jsonPath("$.status").value("PICKED"));
    }

    // -------------------------------------------------------------------------
    // I9 — completeOrder_asSupervisor_returns200AndDeductsStockInDB
    // -------------------------------------------------------------------------

    @Test
    void completeOrder_asSupervisor_returns200AndDeductsStockInDB() throws Exception {
        UUID orderId = createPendingOrder(5, 0);

        // Assign
        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());

        // Pick all items
        String getResult = mockMvc.perform(withOperator(get("/api/v1/picking-orders/{id}", orderId)))
                .andReturn().getResponse().getContentAsString();
        var items = objectMapper.readTree(getResult).get("items");
        for (var item : items) {
            UUID itemId = UUID.fromString(item.get("id").asText());
            int requested = item.get("quantityRequested").asInt();
            mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/pick-item/{itemId}", orderId, itemId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "quantityPicked", requested,
                                    "pickedBy", UUID.randomUUID().toString()))))
                    .andExpect(status().isOk());
        }

        // Complete
        mockMvc.perform(withSupervisor(put("/api/v1/picking-orders/{id}/complete", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verifica reserva liberada no banco
        StockItem updated = stockItemRepository.findById(stockItem1.getId()).orElseThrow();
        assertThat(updated.getQuantityReserved()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // I10 — completeOrder_partial_returns200WithPartialStatus
    // -------------------------------------------------------------------------

    @Test
    void completeOrder_partial_returns200WithPartialStatus() throws Exception {
        UUID orderId = createPendingOrder(10, 0);

        // Assign
        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());

        // Pega o item e coleta parcialmente (5 de 10 → PARTIAL)
        String getResult = mockMvc.perform(withOperator(get("/api/v1/picking-orders/{id}", orderId)))
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(
                objectMapper.readTree(getResult).get("items").get(0).get("id").asText());

        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/pick-item/{itemId}", orderId, itemId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "quantityPicked", 5,
                                "pickedBy", UUID.randomUUID().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));

        // Complete — ordem com 1 item SKIPPED → PARTIAL
        mockMvc.perform(withSupervisor(put("/api/v1/picking-orders/{id}/complete", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));
    }

    // -------------------------------------------------------------------------
    // I11 — cancelOrder_inProgress_asSupervisor_releasesReserveInDB
    // -------------------------------------------------------------------------

    @Test
    void cancelOrder_inProgress_asSupervisor_releasesReserveInDB() throws Exception {
        UUID orderId = createPendingOrder(8, 0);

        // Assign (reserva estoque)
        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());

        // Confirma reserva
        StockItem afterAssign = stockItemRepository.findById(stockItem1.getId()).orElseThrow();
        assertThat(afterAssign.getQuantityReserved()).isEqualTo(8);

        // Cancel
        String cancelBody = objectMapper.writeValueAsString(Map.of("reason", "Cancelamento de teste"));
        mockMvc.perform(withSupervisor(put("/api/v1/picking-orders/{id}/cancel", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Verifica estoque restaurado
        StockItem afterCancel = stockItemRepository.findById(stockItem1.getId()).orElseThrow();
        assertThat(afterCancel.getQuantityReserved()).isEqualTo(0);
        assertThat(afterCancel.getQuantityAvailable()).isEqualTo(50);
    }

    // -------------------------------------------------------------------------
    // I12 — cancelOrder_asOperator_returns403
    // -------------------------------------------------------------------------

    @Test
    void cancelOrder_asOperator_returns403() throws Exception {
        UUID orderId = createPendingOrder(2, 0);
        String cancelBody = objectMapper.writeValueAsString(Map.of("reason", "Tentativa operador"));

        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/cancel", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I13 — listPickingOrders_filterByStatus_returnsPaged
    // -------------------------------------------------------------------------

    @Test
    void listPickingOrders_filterByStatus_returnsPaged() throws Exception {
        // Cria 2 ordens PENDING
        createPendingOrder(1, 0);
        createPendingOrder(2, 0);

        mockMvc.perform(withOperator(get("/api/v1/picking-orders").param("status", "PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    // -------------------------------------------------------------------------
    // I14 — getPickingOrder_withItemsSortedBySortOrder_returns200
    // -------------------------------------------------------------------------

    @Test
    void getPickingOrder_withItemsSortedBySortOrder_returns200() throws Exception {
        // Cria ordem com 2 produtos em aisles diferentes
        String body = objectMapper.writeValueAsString(Map.of(
                "warehouseId", warehouseId.toString(),
                "priority", 0,
                "items", List.of(
                        Map.of("productId", product1.getId().toString(), "quantity", 1),
                        Map.of("productId", product2.getId().toString(), "quantity", 1)
                )
        ));

        String createResult = mockMvc.perform(withSupervisor(post("/api/v1/picking-orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(createResult).get("id").asText());

        // Assign para gerar sort_order
        mockMvc.perform(withOperator(put("/api/v1/picking-orders/{id}/assign", orderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("operatorId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk());

        // GET — verifica que os itens retornam ordenados por sort_order
        String getResult = mockMvc.perform(withOperator(get("/api/v1/picking-orders/{id}", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        var items = objectMapper.readTree(getResult).get("items");
        int so1 = items.get(0).get("sortOrder").asInt();
        int so2 = items.get(1).get("sortOrder").asInt();
        assertThat(so1).isLessThanOrEqualTo(so2);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Cria uma PickingOrder PENDING via POST (com product1, qty=requestedQty).
     * Retorna o ID da ordem criada.
     */
    private UUID createPendingOrder(int requestedQty, int product2Qty) throws Exception {
        var itemsList = product2Qty > 0
                ? List.of(
                        Map.of("productId", product1.getId().toString(), "quantity", requestedQty),
                        Map.of("productId", product2.getId().toString(), "quantity", product2Qty))
                : List.of(Map.of("productId", product1.getId().toString(), "quantity", requestedQty));

        String body = objectMapper.writeValueAsString(Map.of(
                "warehouseId", warehouseId.toString(),
                "priority", 0,
                "items", itemsList
        ));

        String result = mockMvc.perform(withSupervisor(post("/api/v1/picking-orders"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(result).get("id").asText());
    }

    private MockHttpServletRequestBuilder withOperator(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private MockHttpServletRequestBuilder withSupervisor(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_SUPERVISOR")));
    }
}
