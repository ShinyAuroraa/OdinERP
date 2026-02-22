package com.odin.wms.traceability;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.*;
import com.odin.wms.domain.repository.*;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityIndexer;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para TraceabilityController e GS1Controller.
 * AC1, AC2, AC3, AC4, AC5, AC6, AC8.
 *
 * TraceabilityRepository é mockado (@MockBean) para retornar vazio,
 * forçando o fallback ao PostgreSQL e isolando a camada ES.
 * TraceabilityIndexer é mockado para evitar indexação assíncrona nos testes.
 */
@AutoConfigureMockMvc
class TraceabilityControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private LotRepository lotRepository;
    @Autowired private SerialNumberRepository serialNumberRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private StockMovementRepository stockMovementRepository;

    // Isola ES dos testes — fallback automático ao PostgreSQL
    @MockBean private TraceabilityRepository traceabilityRepository;
    @MockBean private TraceabilityIndexer traceabilityIndexer;

    private UUID tenantId;
    private ProductWms product;
    private Lot lot;
    private Location storageLocation;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        // ES sempre retorna vazio → service usa PostgreSQL como fallback
        when(traceabilityRepository.findByTenantIdAndLotNumberOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(traceabilityRepository.findByTenantIdAndSerialNumberOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());

        // Hierarquia de localização
        Warehouse wh = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-TR-" + tenantId).name("WH Rastr").build());
        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(wh)
                .code("Z-TR").name("Zona TR").type(LocationType.STORAGE).build());
        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-TR").build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-TR").level(1).build());
        storageLocation = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf)
                .code("LOC-TR").fullAddress("WH-TR.Z-TR.A-TR.S-TR.LOC-TR")
                .type(LocationType.STORAGE).capacityUnits(100).active(true).build());

        // Produto
        product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-RASTR").name("Produto Rastreável")
                .storageType(StorageType.DRY)
                .controlsLot(true).controlsSerial(true).controlsExpiry(true)
                .active(true).build());

        // Lote com validade
        lot = lotRepository.save(Lot.builder()
                .tenantId(tenantId)
                .product(product)
                .lotNumber("LOT-INTEGR-001")
                .expiryDate(LocalDate.of(2027, 6, 30))
                .active(true).build());

        // StockItem disponível para getExpiryByProduct
        stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(storageLocation)
                .product(product).lot(lot)
                .quantityAvailable(15).build());

        // StockMovement de recebimento para getLotHistory / getTraceabilityTree
        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .type(MovementType.INBOUND)
                .product(product).lot(lot)
                .destinationLocation(storageLocation)
                .quantity(15)
                .referenceType(ReferenceType.RECEIVING_NOTE)
                .referenceId(UUID.randomUUID())
                .operatorId(UUID.randomUUID())
                .build());
    }

    // -------------------------------------------------------------------------
    // Helpers JWT
    // -------------------------------------------------------------------------

    private MockHttpServletRequestBuilder withAdminJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString()).claim("sub", "admin-sub"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }

    private MockHttpServletRequestBuilder withSupervisorJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString()).claim("sub", "sup-sub"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_SUPERVISOR")));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/traceability/lot/{lotNumber} — AC1
    // -------------------------------------------------------------------------

    @Test
    void getLotHistory_validLot_returns200WithMovements() throws Exception {
        mockMvc.perform(withAdminJwt(get("/api/v1/traceability/lot/LOT-INTEGR-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value("LOT-INTEGR-001"))
                .andExpect(jsonPath("$.movements", hasSize(1)))
                .andExpect(jsonPath("$.movements[0].movementType").value("INBOUND"))
                .andExpect(jsonPath("$.totalMovements").value(1));
    }

    @Test
    void getLotHistory_unknownLot_returns404() throws Exception {
        mockMvc.perform(withAdminJwt(get("/api/v1/traceability/lot/LOT-INEXISTENTE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLotHistory_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/traceability/lot/LOT-INTEGR-001"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * CONCERN-4 carry-forward: WMS_SUPERVISOR deve ter acesso aos endpoints de rastreabilidade.
     */
    @Test
    void getLotHistory_withSupervisorRole_returns200() throws Exception {
        mockMvc.perform(withSupervisorJwt(get("/api/v1/traceability/lot/LOT-INTEGR-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value("LOT-INTEGR-001"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/traceability/serial/{serialNumber} — AC2
    // -------------------------------------------------------------------------

    @Test
    void getSerialHistory_validSerial_returns200() throws Exception {
        serialNumberRepository.save(SerialNumber.builder()
                .tenantId(tenantId)
                .product(product)
                .serialNumber("SER-INTEGR-001")
                .status(SerialStatus.IN_STOCK)
                .location(storageLocation)
                .build());

        mockMvc.perform(withAdminJwt(get("/api/v1/traceability/serial/SER-INTEGR-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serialNumber").value("SER-INTEGR-001"))
                .andExpect(jsonPath("$.currentStatus").value("IN_STOCK"));
    }

    @Test
    void getSerialHistory_unknownSerial_returns404() throws Exception {
        mockMvc.perform(withAdminJwt(get("/api/v1/traceability/serial/SER-INEXISTENTE")))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/traceability/lot/{lotId}/tree — AC3
    // -------------------------------------------------------------------------

    @Test
    void getTraceabilityTree_validLot_returns200WithEvents() throws Exception {
        mockMvc.perform(withAdminJwt(
                        get("/api/v1/traceability/lot/" + lot.getId() + "/tree")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value("LOT-INTEGR-001"))
                .andExpect(jsonPath("$.events", not(empty())));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/traceability/product/{productId}/expiry — AC4
    // -------------------------------------------------------------------------

    @Test
    void getExpiryByProduct_validProduct_returns200FefoSorted() throws Exception {
        // productId aqui é o JPA entity ID (BaseEntity.id) do ProductWms
        mockMvc.perform(withAdminJwt(
                        get("/api/v1/traceability/product/" + product.getId() + "/expiry")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].lotNumber").value("LOT-INTEGR-001"))
                .andExpect(jsonPath("$[0].quantityAvailable").value(15));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/gs1/parse — AC5
    // -------------------------------------------------------------------------

    @Test
    void parseGs1_validGs1128_returns200WithParsedFields() throws Exception {
        String body = """
                {"barcode":"(01)07891234567895(10)LOTE-01(17)261231(21)SER-001","format":"GS1_128"}
                """;

        mockMvc.perform(withAdminJwt(post("/api/v1/gs1/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gtin").value("07891234567895"))
                .andExpect(jsonPath("$.lotNumber").value("LOTE-01"))
                .andExpect(jsonPath("$.serialNumber").value("SER-001"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/gs1/generate — AC6
    // -------------------------------------------------------------------------

    @Test
    void generateGs1_validGtin_returns200WithAllCodes() throws Exception {
        mockMvc.perform(withAdminJwt(get("/api/v1/gs1/generate")
                        .param("gtin", "07891234567895")
                        .param("lotNumber", "LOTE-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ean13").value("7891234567895"))
                .andExpect(jsonPath("$.gs1128").value(containsString("(01)07891234567895")));
    }
}
