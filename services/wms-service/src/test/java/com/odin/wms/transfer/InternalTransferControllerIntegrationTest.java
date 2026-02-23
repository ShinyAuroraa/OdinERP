package com.odin.wms.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.entity.InternalTransfer.TransferStatus;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
import com.odin.wms.messaging.StockMovementEventPublisher;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para InternalTransferController — I1–I9.
 */
@AutoConfigureMockMvc
class InternalTransferControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private InternalTransferRepository internalTransferRepository;

    @MockBean private StockMovementEventPublisher stockMovementEventPublisher;
    @MockBean private AuditLogIndexer auditLogIndexer;

    private UUID tenantId;
    private Location sourceLocation;
    private Location destLocation;
    private ProductWms product;
    private StockItem stockItem;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-TR-" + tenantId).name("Armazém Transfer").build());

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh).code("Z-TR-" + tenantId)
                .name("Zona Transfer").type(LocationType.STORAGE).build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-TR-" + tenantId).build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-TR-" + tenantId).level(1).build());

        sourceLocation = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-SRC-" + tenantId)
                .fullAddress("WH-TR/Z-TR/A-TR/S-TR/L-SRC")
                .type(LocationType.STORAGE).capacityUnits(100).active(true).build());

        destLocation = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-DST-" + tenantId)
                .fullAddress("WH-TR/Z-TR/A-TR/S-TR/L-DST")
                .type(LocationType.STORAGE).capacityUnits(100).active(true).build());

        product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-TR-" + tenantId).name("Produto Transfer")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        stockItem = stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(sourceLocation).product(product)
                .quantityAvailable(50).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0)
                .receivedAt(Instant.now()).build());
    }

    // -------------------------------------------------------------------------
    // I1 — createTransfer_asOperator_returns201
    // -------------------------------------------------------------------------

    @Test
    void createTransfer_asOperator_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 10,
                "reason", "Reabastecimento"
        ));

        mockMvc.perform(withOperator(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.transferType").value("MANUAL"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    // -------------------------------------------------------------------------
    // I2 — createTransfer_asViewer_returns403
    // -------------------------------------------------------------------------

    @Test
    void createTransfer_asViewer_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 5
        ));

        mockMvc.perform(withViewer(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I3 — confirmTransfer_asOperator_returns200AndMovesStock
    // -------------------------------------------------------------------------

    @Test
    void confirmTransfer_asOperator_returns200AndMovesStock() throws Exception {
        UUID operatorId = UUID.randomUUID();

        // Cria transferência
        String createBody = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 20
        ));

        String createResult = mockMvc.perform(withOperator(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(
                objectMapper.readTree(createResult).get("id").asText());

        // Confirma
        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "confirmedBy", operatorId.toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/transfers/{id}/confirm", transferId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedBy").value(operatorId.toString()));

        // Verifica stock atualizado no banco
        StockItem updatedSource = stockItemRepository.findById(stockItem.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updatedSource.getQuantityAvailable()).isEqualTo(30); // 50 - 20

        StockItem destItem = stockItemRepository
                .findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
                        tenantId, destLocation.getId(), product.getId())
                .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(destItem.getQuantityAvailable()).isEqualTo(20);
    }

    // -------------------------------------------------------------------------
    // I4 — confirmTransfer_insufficientStock_returns400
    // -------------------------------------------------------------------------

    @Test
    void confirmTransfer_insufficientStock_returns400() throws Exception {
        // Cria transferência com quantidade maior que o disponível (50)
        String createBody = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 100
        ));

        String createResult = mockMvc.perform(withOperator(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(
                objectMapper.readTree(createResult).get("id").asText());

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "confirmedBy", UUID.randomUUID().toString()
        ));

        mockMvc.perform(withOperator(put("/api/v1/transfers/{id}/confirm", transferId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Estoque insuficiente")));
    }

    // -------------------------------------------------------------------------
    // I5 — cancelTransfer_asSupervisor_returns200
    // -------------------------------------------------------------------------

    @Test
    void cancelTransfer_asSupervisor_returns200() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 5
        ));

        String createResult = mockMvc.perform(withOperator(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(
                objectMapper.readTree(createResult).get("id").asText());

        String cancelBody = objectMapper.writeValueAsString(Map.of("reason", "Erro no pedido"));

        mockMvc.perform(withSupervisor(put("/api/v1/transfers/{id}/cancel", transferId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // -------------------------------------------------------------------------
    // I6 — cancelTransfer_asOperator_returns403
    // -------------------------------------------------------------------------

    @Test
    void cancelTransfer_asOperator_returns403() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 5
        ));

        String createResult = mockMvc.perform(withOperator(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(
                objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(withOperator(put("/api/v1/transfers/{id}/cancel", transferId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I7 — getTransfer_sameTenant_returns200
    // -------------------------------------------------------------------------

    @Test
    void getTransfer_sameTenant_returns200() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 3
        ));

        String createResult = mockMvc.perform(withOperator(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(
                objectMapper.readTree(createResult).get("id").asText());

        mockMvc.perform(withOperator(get("/api/v1/transfers/{id}", transferId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // -------------------------------------------------------------------------
    // I8 — getTransfer_differentTenant_returns403
    // -------------------------------------------------------------------------

    @Test
    void getTransfer_differentTenant_returns403() throws Exception {
        // Cria transferência no tenant original
        String createBody = objectMapper.writeValueAsString(Map.of(
                "sourceLocationId", sourceLocation.getId().toString(),
                "destinationLocationId", destLocation.getId().toString(),
                "productId", product.getId().toString(),
                "quantity", 3
        ));

        String createResult = mockMvc.perform(withOperator(post("/api/v1/transfers"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID transferId = UUID.fromString(
                objectMapper.readTree(createResult).get("id").asText());

        // Acessa com tenant diferente
        UUID otherTenantId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/transfers/{id}", transferId)
                        .with(jwt()
                                .jwt(j -> j.claim("tenant_id", otherTenantId.toString())
                                           .claim("sub", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I9 — listTransfers_filterByStatus_returnsPaged
    // -------------------------------------------------------------------------

    @Test
    void listTransfers_filterByStatus_returnsPaged() throws Exception {
        // Cria 2 transferências
        for (int i = 0; i < 2; i++) {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sourceLocationId", sourceLocation.getId().toString(),
                    "destinationLocationId", destLocation.getId().toString(),
                    "productId", product.getId().toString(),
                    "quantity", 1
            ));
            mockMvc.perform(withOperator(post("/api/v1/transfers"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(withOperator(get("/api/v1/transfers").param("status", "PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    // -------------------------------------------------------------------------
    // Test Helpers
    // -------------------------------------------------------------------------

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

    private MockHttpServletRequestBuilder withViewer(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER")));
    }

}
