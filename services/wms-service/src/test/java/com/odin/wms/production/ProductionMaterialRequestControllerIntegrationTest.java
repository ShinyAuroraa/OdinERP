package com.odin.wms.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.ProductionMaterialRequest.MaterialRequestStatus;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.FinishedGoodsReceivedEventPublisher;
import com.odin.wms.messaging.MaterialsDeliveredEventPublisher;
import com.odin.wms.messaging.StockShortageEventPublisher;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para ProductionMaterialRequestController — I1–I12.
 */
@AutoConfigureMockMvc
class ProductionMaterialRequestControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private ProductionMaterialRequestRepository requestRepository;
    @Autowired private ProductionMaterialRequestItemRepository itemRepository;
    @Autowired private PickingOrderRepository pickingOrderRepository;

    @MockBean private AuditLogIndexer auditLogIndexer;
    @MockBean private MaterialsDeliveredEventPublisher materialsDeliveredEventPublisher;
    @MockBean private FinishedGoodsReceivedEventPublisher finishedGoodsReceivedEventPublisher;
    @MockBean private StockShortageEventPublisher stockShortageEventPublisher;

    private UUID tenantId;
    private Warehouse warehouse;
    private Location location;
    private ProductWms product;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        warehouse = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-PMR-" + tenantId).name("Armazém PMR").build());

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(warehouse).code("Z-PMR-" + tenantId)
                .name("Zona PMR").type(LocationType.STORAGE).build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-PMR-" + tenantId).build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-PMR-" + tenantId).level(1).build());

        location = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-PMR-" + tenantId)
                .fullAddress("WH-PMR/Z-PMR/A-PMR/S-PMR/L-PMR")
                .type(LocationType.STORAGE).capacityUnits(100).active(true).build());

        product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-PMR-" + tenantId).name("Matéria-Prima PMR")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());
    }

    // -------------------------------------------------------------------------
    // I1 — getRequest: 200 com tenantId correto
    // -------------------------------------------------------------------------

    @Test
    void getRequest_sameTenant_returns200() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.PENDING);

        mockMvc.perform(withOperator(get("/api/v1/production-material-requests/{id}", req.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(req.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // -------------------------------------------------------------------------
    // I2 — getRequest: 404 com ID inexistente
    // -------------------------------------------------------------------------

    @Test
    void getRequest_notFound_returns404() throws Exception {
        mockMvc.perform(withOperator(get("/api/v1/production-material-requests/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // I3 — getRequest: 401 sem autenticação
    // -------------------------------------------------------------------------

    @Test
    void getRequest_unauthenticated_returns401() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.PENDING);

        mockMvc.perform(get("/api/v1/production-material-requests/{id}", req.getId()))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // I4 — listRequests: 200 sem filtro
    // -------------------------------------------------------------------------

    @Test
    void listRequests_noFilter_returns200WithItems() throws Exception {
        createSavedRequest(MaterialRequestStatus.PENDING);
        createSavedRequest(MaterialRequestStatus.STOCK_SHORTAGE);

        mockMvc.perform(withOperator(get("/api/v1/production-material-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    // -------------------------------------------------------------------------
    // I5 — listRequests: filtro por status
    // -------------------------------------------------------------------------

    @Test
    void listRequests_filterByStatus_returnsonlyMatchingStatus() throws Exception {
        createSavedRequest(MaterialRequestStatus.PENDING);
        createSavedRequest(MaterialRequestStatus.PICKING_PENDING);

        mockMvc.perform(withOperator(get("/api/v1/production-material-requests")
                        .param("status", "PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", everyItem(is("PENDING"))));
    }

    // -------------------------------------------------------------------------
    // I6 — confirmDelivery: PICKING_PENDING → 200 DELIVERED
    // -------------------------------------------------------------------------

    @Test
    void confirmDelivery_fromPickingPending_returns200Delivered() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.PICKING_PENDING);

        mockMvc.perform(withOperator(
                        post("/api/v1/production-material-requests/{id}/confirm-delivery", req.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    // -------------------------------------------------------------------------
    // I7 — confirmDelivery: status PENDING → 409 Conflict
    // -------------------------------------------------------------------------

    @Test
    void confirmDelivery_fromPending_returns409() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.PENDING);

        mockMvc.perform(withOperator(
                        post("/api/v1/production-material-requests/{id}/confirm-delivery", req.getId())))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // I8 — receiveFinishedGoods: DELIVERED → 200 FINISHED_GOODS_RECEIVED
    // -------------------------------------------------------------------------

    @Test
    void receiveFinishedGoods_fromDelivered_returns200() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.DELIVERED);

        stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(location).product(product)
                .quantityAvailable(20).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0)
                .receivedAt(Instant.now()).build());

        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of(
                        "productId", product.getId().toString(),
                        "locationId", location.getId().toString(),
                        "quantityReceived", 5
                ))
        ));

        mockMvc.perform(withOperator(
                        post("/api/v1/production-material-requests/{id}/receive-finished-goods", req.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED_GOODS_RECEIVED"));
    }

    // -------------------------------------------------------------------------
    // I9 — receiveFinishedGoods: status inválido → 409
    // -------------------------------------------------------------------------

    @Test
    void receiveFinishedGoods_notDelivered_returns409() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.PENDING);

        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of(
                        "productId", product.getId().toString(),
                        "locationId", location.getId().toString(),
                        "quantityReceived", 1
                ))
        ));

        mockMvc.perform(withOperator(
                        post("/api/v1/production-material-requests/{id}/receive-finished-goods", req.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // I10 — receiveFinishedGoods: body inválido → 400
    // -------------------------------------------------------------------------

    @Test
    void receiveFinishedGoods_emptyItems_returns400() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.DELIVERED);

        String body = objectMapper.writeValueAsString(Map.of("items", List.of()));

        mockMvc.perform(withOperator(
                        post("/api/v1/production-material-requests/{id}/receive-finished-goods", req.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // I11 — confirmDelivery: role WMS_VIEWER → 403
    // -------------------------------------------------------------------------

    @Test
    void confirmDelivery_withViewerRole_returns403() throws Exception {
        ProductionMaterialRequest req = createSavedRequest(MaterialRequestStatus.PICKING_PENDING);

        mockMvc.perform(withViewer(
                        post("/api/v1/production-material-requests/{id}/confirm-delivery", req.getId())))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I12 — listRequests: isolamento de tenant — não retorna dados de outro tenant
    // -------------------------------------------------------------------------

    @Test
    void listRequests_isolatedByTenant_doesNotShowOtherTenantData() throws Exception {
        // Cria PMR no tenant atual
        createSavedRequest(MaterialRequestStatus.PENDING);

        // Cria PMR em outro tenant
        UUID otherTenant = UUID.randomUUID();
        requestRepository.save(ProductionMaterialRequest.builder()
                .tenantId(otherTenant)
                .productionOrderId(UUID.randomUUID())
                .warehouseId(warehouse.getId())
                .build());

        // O tenant atual NÃO deve ver o PMR do outro tenant
        mockMvc.perform(withOperator(get("/api/v1/production-material-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id",
                        not(hasItem(containsString(otherTenant.toString())))));
    }

    // -------------------------------------------------------------------------
    // Test Helpers
    // -------------------------------------------------------------------------

    private ProductionMaterialRequest createSavedRequest(MaterialRequestStatus status) {
        return requestRepository.save(ProductionMaterialRequest.builder()
                .tenantId(tenantId)
                .productionOrderId(UUID.randomUUID())
                .mrpOrderNumber("OP-TEST-" + UUID.randomUUID())
                .warehouseId(warehouse.getId())
                .status(status)
                .build());
    }

    private MockHttpServletRequestBuilder withOperator(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private MockHttpServletRequestBuilder withViewer(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER")));
    }
}
