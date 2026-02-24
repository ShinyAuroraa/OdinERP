package com.odin.wms.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.*;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReportType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.domain.repository.*;
import com.odin.wms.dto.request.ReportScheduleRequest;
import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.infrastructure.elasticsearch.AuditLogIndexer;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para relatórios regulatórios — I1–I13.
 */
@AutoConfigureMockMvc
class ReportControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private AisleRepository aisleRepository;
    @Autowired private ShelfRepository shelfRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProductWmsRepository productWmsRepository;
    @Autowired private StockItemRepository stockItemRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private LotRepository lotRepository;
    @Autowired private ReportScheduleRepository scheduleRepository;

    @MockBean private AuditLogIndexer auditLogIndexer;

    private UUID tenantId;
    private Warehouse warehouse;
    private Location location;
    private ProductWms product;
    private Lot lot;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        warehouse = warehouseRepository.save(Warehouse.builder()
                .tenantId(tenantId).code("WH-RPT-" + tenantId).name("Armazém Relatórios").build());

        Zone zone = zoneRepository.save(Zone.builder()
                .tenantId(tenantId).warehouse(warehouse).code("Z-RPT-" + tenantId)
                .name("Zona RPT").type(LocationType.STORAGE).build());

        Aisle aisle = aisleRepository.save(Aisle.builder()
                .tenantId(tenantId).zone(zone).code("A-RPT-" + tenantId).build());
        Shelf shelf = shelfRepository.save(Shelf.builder()
                .tenantId(tenantId).aisle(aisle).code("S-RPT-" + tenantId).level(1).build());

        location = locationRepository.save(Location.builder()
                .tenantId(tenantId).shelf(shelf).code("L-RPT-" + tenantId)
                .fullAddress("WH-RPT/Z-RPT/A-RPT/S-RPT/L-RPT")
                .type(LocationType.STORAGE).capacityUnits(100).active(true).build());

        product = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-RPT-" + tenantId).name("Produto Relatório")
                .storageType(StorageType.DRY).controlsLot(false)
                .controlsSerial(false).controlsExpiry(false).active(true).build());

        lot = lotRepository.save(Lot.builder()
                .tenantId(tenantId).product(product)
                .lotNumber("LOT-RPT-" + tenantId).active(true).build());

        stockItemRepository.save(StockItem.builder()
                .tenantId(tenantId).location(location).product(product).lot(lot)
                .quantityAvailable(50).quantityReserved(0)
                .quantityQuarantine(0).quantityDamaged(0)
                .receivedAt(Instant.now()).build());

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId).product(product).lot(lot)
                .type(MovementType.INBOUND)
                .destinationLocation(location)
                .quantity(50).operatorId(UUID.randomUUID())
                .referenceType(com.odin.wms.domain.enums.ReferenceType.RECEIVING_NOTE)
                .referenceId(UUID.randomUUID()).build());
    }

    // -------------------------------------------------------------------------
    // I1 — fichaEstoque: 200 JSON
    // -------------------------------------------------------------------------

    @Test
    void I1_fichaEstoque_authenticated_returns200Json() throws Exception {
        mockMvc.perform(withSupervisor(get("/api/v1/reports/ficha-estoque")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")
                        .param("format", "JSON")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // -------------------------------------------------------------------------
    // I2 — fichaEstoque: formato PDF → 200 com Content-Type application/pdf
    // -------------------------------------------------------------------------

    @Test
    void I2_fichaEstoque_formatPdf_returns200WithPdfContentType() throws Exception {
        mockMvc.perform(withSupervisor(get("/api/v1/reports/ficha-estoque")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")
                        .param("format", "PDF")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    // -------------------------------------------------------------------------
    // I3 — fichaEstoque: formato EXCEL → 200 com Content-Type spreadsheet
    // -------------------------------------------------------------------------

    @Test
    void I3_fichaEstoque_formatExcel_returns200WithExcelContentType() throws Exception {
        mockMvc.perform(withSupervisor(get("/api/v1/reports/ficha-estoque")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")
                        .param("format", "EXCEL")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    // -------------------------------------------------------------------------
    // I4 — fichaEstoque: formato XML → 200 com Content-Type application/xml
    // -------------------------------------------------------------------------

    @Test
    void I4_fichaEstoque_formatXml_returns200WithXmlContentType() throws Exception {
        mockMvc.perform(withSupervisor(get("/api/v1/reports/ficha-estoque")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")
                        .param("format", "XML")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }

    // -------------------------------------------------------------------------
    // I5 — movimentacoes: com filtros retorna 200
    // -------------------------------------------------------------------------

    @Test
    void I5_movimentacoes_withFilters_returns200() throws Exception {
        mockMvc.perform(withOperator(get("/api/v1/reports/movimentacoes")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")
                        .param("productId", product.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // -------------------------------------------------------------------------
    // I6 — rastreabilidade-lote: lote válido retorna 200
    // -------------------------------------------------------------------------

    @Test
    void I6_rastreabilidadeLote_validLot_returns200() throws Exception {
        mockMvc.perform(withSupervisor(
                        get("/api/v1/reports/rastreabilidade-lote/{id}", lot.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // -------------------------------------------------------------------------
    // I7 — rastreabilidade-lote: lote inexistente → 404
    // -------------------------------------------------------------------------

    @Test
    void I7_rastreabilidadeLote_unknownLot_returns404() throws Exception {
        mockMvc.perform(withSupervisor(
                        get("/api/v1/reports/rastreabilidade-lote/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // I8 — anvisa: sem produtos com vigilância → 200 lista vazia
    // -------------------------------------------------------------------------

    @Test
    void I8_anvisa_noVigilanciaProducts_returns200EmptyList() throws Exception {
        // produto criado no setUp não tem vigilanciaSanitaria=true
        mockMvc.perform(withSupervisor(get("/api/v1/reports/anvisa")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    // -------------------------------------------------------------------------
    // I9 — createSchedule: request válido → 201
    // -------------------------------------------------------------------------

    @Test
    void I9_createSchedule_validRequest_returns201() throws Exception {
        var body = objectMapper.writeValueAsString(Map.of(
                "reportType", "FICHA_ESTOQUE",
                "format", "PDF",
                "cronExpression", "0 0 8 * * MON",
                "warehouseId", warehouse.getId().toString(),
                "filters", Map.of()
        ));

        mockMvc.perform(withAdmin(post("/api/v1/reports/schedules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportType").value("FICHA_ESTOQUE"))
                .andExpect(jsonPath("$.nextExecutionAt").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // I10 — fichaEstoque: sem autenticação → 401
    // -------------------------------------------------------------------------

    @Test
    void I10_fichaEstoque_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/ficha-estoque")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-01-31"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // I11 — fichaEstoque com ROLE_WMS_OPERATOR → 403 (role insuficiente)
    // -------------------------------------------------------------------------

    @Test
    void I11_fichaEstoque_withOperatorRole_returns403() throws Exception {
        mockMvc.perform(withOperator(get("/api/v1/reports/ficha-estoque")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I12 — deleteSchedule: agendamento existente → 204 No Content
    // -------------------------------------------------------------------------

    @Test
    void I12_deleteSchedule_validId_returns204() throws Exception {
        ReportSchedule schedule = scheduleRepository.save(ReportSchedule.builder()
                .tenantId(tenantId)
                .reportType(ReportType.FICHA_ESTOQUE)
                .exportFormat(ExportFormat.PDF)
                .cronExpression("0 0 8 * * MON")
                .warehouseId(warehouse.getId())
                .active(true)
                .build());

        mockMvc.perform(withAdmin(delete("/api/v1/reports/schedules/{id}", schedule.getId())))
                .andExpect(status().isNoContent());
    }

    // -------------------------------------------------------------------------
    // I13 — anvisa: produto com vigilanciaSanitaria=true e movimentos → lista não vazia
    // -------------------------------------------------------------------------

    @Test
    void I13_anvisa_withVigilanciaProduct_returnsNonEmptyList() throws Exception {
        ProductWms anvisaProduct = productWmsRepository.save(ProductWms.builder()
                .tenantId(tenantId).productId(UUID.randomUUID())
                .sku("SKU-ANVISA-" + tenantId).name("Medicamento Controlado")
                .storageType(StorageType.DRY).controlsLot(true)
                .controlsSerial(false).controlsExpiry(false)
                .vigilanciaSanitaria(true).active(true).build());

        Lot anvisaLot = lotRepository.save(Lot.builder()
                .tenantId(tenantId).product(anvisaProduct)
                .lotNumber("LOT-ANVISA-" + tenantId).active(true).build());

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId).product(anvisaProduct).lot(anvisaLot)
                .type(MovementType.INBOUND)
                .destinationLocation(location)
                .quantity(20).operatorId(UUID.randomUUID())
                .referenceType(com.odin.wms.domain.enums.ReferenceType.RECEIVING_NOTE)
                .referenceId(UUID.randomUUID()).build());

        mockMvc.perform(withSupervisor(get("/api/v1/reports/anvisa")
                        .param("warehouseId", warehouse.getId().toString())
                        .param("dataInicio", "2026-01-01")
                        .param("dataFim", "2026-12-31")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sku").value("SKU-ANVISA-" + tenantId))
                .andExpect(jsonPath("$[0].quantityReceived").value(20));
    }

    // -------------------------------------------------------------------------
    // Test Helpers
    // -------------------------------------------------------------------------

    private MockHttpServletRequestBuilder withSupervisor(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_SUPERVISOR")));
    }

    private MockHttpServletRequestBuilder withOperator(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private MockHttpServletRequestBuilder withAdmin(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }
}
