package com.odin.wms.quarantine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.QuarantineDecision;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.dto.request.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class QuarantineControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Kafka desabilitado no perfil test — KafkaTemplate precisa ser mockado. */
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUpKafkaMock() {
        // KafkaTemplate.send() retorna CompletableFuture — sem stub retorna null e causa NPE no whenComplete().
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private MockHttpServletRequestBuilder withOperatorJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", TEST_TENANT_ID.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private MockHttpServletRequestBuilder withSupervisorJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", TEST_TENANT_ID.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_SUPERVISOR")));
    }

    private MockHttpServletRequestBuilder withAdminJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", TEST_TENANT_ID.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }

    /** JWT com tenant_id diferente — usado para testar isolamento multi-tenant (AC11). */
    private MockHttpServletRequestBuilder withDifferentTenantJwt(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", UUID.randomUUID().toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    /**
     * Cria WH → Zone → Aisle → Shelf → DOCK + QUARANTINE + STORAGE locations.
     * QUARANTINE é exigido por generateTasks().
     * STORAGE é exigido pelo motor de sugestão RELEASE_TO_STOCK.
     */
    private QuarantineFixture createQuarantineFixture(String suffix) throws Exception {
        // Warehouse
        String whJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest("WH-QC-" + suffix, "WH QC " + suffix, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID warehouseId = readUUID(whJson, "id");

        // Zone
        String znJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateZoneRequest("ZN-QC-" + suffix, "ZN QC", LocationType.STORAGE)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID zoneId = readUUID(znJson, "id");

        // Aisle
        String aiJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/zones/{id}/aisles", zoneId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAisleRequest("A-QC-" + suffix, "Aisle QC")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID aisleId = readUUID(aiJson, "id");

        // Shelf
        String shJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aisleId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShelfRequest("S-QC-" + suffix, 1)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID shelfId = readUUID(shJson, "id");

        // RECEIVING_DOCK location
        String dockJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("DOCK-QC-" + suffix, LocationType.RECEIVING_DOCK, null, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID dockLocationId = readUUID(dockJson, "id");

        // QUARANTINE location — necessária para generateTasks()
        String qLocJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("QUAR-QC-" + suffix, LocationType.QUARANTINE, null, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID quarantineLocationId = readUUID(qLocJson, "id");

        // STORAGE location — necessária para RELEASE_TO_STOCK
        String storJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("STOR-QC-" + suffix, LocationType.STORAGE, null, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID storageLocationId = readUUID(storJson, "id");

        return new QuarantineFixture(warehouseId, dockLocationId, quarantineLocationId, storageLocationId);
    }

    private UUID createProduct(String sku) throws Exception {
        String json = mockMvc.perform(withAdminJwt(
                post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProductWmsRequest(
                                        UUID.randomUUID(), sku, "Produto " + sku, StorageType.DRY,
                                        false, false, false,
                                        null, null, null, null, null, null, null, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return readUUID(json, "id");
    }

    /**
     * Cria nota → inicia → confirma item com qty=0 (→ FLAGGED/DAMAGED) → completa.
     * Confirmar com qty=0 resulta em FLAGGED e a nota fica COMPLETED_WITH_DIVERGENCE.
     * Retorna o noteId.
     */
    private UUID createDivergenceNote(QuarantineFixture fix, UUID productId) throws Exception {
        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 5)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");
        UUID itemId = readUUID(objectMapper.readTree(noteJson).get("items").get(0).toString(), "id");

        // Start
        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/start", noteId)))
                .andExpect(status().isOk());

        // Confirma com qty=0 → FLAGGED (DivergenceType.DAMAGED) → nota COMPLETED_WITH_DIVERGENCE
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/items/{itemId}/confirm", noteId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmReceivingItemRequest(0, null, null, null, null, null)))))
                .andExpect(status().isOk());

        // Complete → COMPLETED_WITH_DIVERGENCE
        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/complete", noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_DIVERGENCE"));

        return noteId;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void generateTasks_afterDivergenceNote_returns201WithQuarantineLocation() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("GT01");
        UUID productId = createProduct("SKU-QC-GT01");
        UUID noteId = createDivergenceNote(fix, productId);

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].quarantineLocationId").value(fix.quarantineLocationId().toString()))
                .andExpect(jsonPath("$[0].sourceLocationId").value(fix.dockLocationId().toString()));
    }

    @Test
    void generateTasks_duplicateCall_returns409() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("GT02");
        UUID productId = createProduct("SKU-QC-GT02");
        UUID noteId = createDivergenceNote(fix, productId);

        // Primeira chamada — sucesso
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated());

        // Segunda chamada — conflito
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isConflict());
    }

    @Test
    void generateTasks_noteNotCompletedWithDivergence_returns422() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("GT03");
        UUID productId = createProduct("SKU-QC-GT03");

        // Nota em COMPLETED (sem divergências — confirma qty=5 exato)
        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 5)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");
        UUID itemId = readUUID(objectMapper.readTree(noteJson).get("items").get(0).toString(), "id");

        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/start", noteId)));

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/items/{itemId}/confirm", noteId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmReceivingItemRequest(5, null, null, null, null, null)))));

        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/complete", noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Tenta gerar quarantine tasks em nota sem divergências → 422
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void startTask_withSupervisorRole_returns200InReview() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("ST01");
        UUID productId = createProduct("SKU-QC-ST01");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/start", taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
    }

    @Test
    void startTask_withOperatorRole_returns403() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("ST02");
        UUID productId = createProduct("SKU-QC-ST02");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // OPERATOR não tem autorização para iniciar inspeção de QC
        mockMvc.perform(withOperatorJwt(
                patch("/api/v1/quarantine-tasks/{id}/start", taskId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void decideTask_releaseToStock_returns200Approved() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("DC01");
        UUID productId = createProduct("SKU-QC-DC01");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // Start → IN_REVIEW
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/start", taskId)))
                .andExpect(status().isOk());

        // Decide RELEASE_TO_STOCK
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/decide", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DecideQuarantineRequest(QuarantineDecision.RELEASE_TO_STOCK, "Item aprovado no QC")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decision").value("RELEASE_TO_STOCK"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void decideTask_scrap_returns200Rejected() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("DC02");
        UUID productId = createProduct("SKU-QC-DC02");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // Start → IN_REVIEW
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/start", taskId)))
                .andExpect(status().isOk());

        // Decide SCRAP
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/decide", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DecideQuarantineRequest(QuarantineDecision.SCRAP, "Produto danificado")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision").value("SCRAP"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void cancelTask_withSupervisorRole_returns204() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("CANC01");
        UUID productId = createProduct("SKU-QC-CANC01");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/cancel", taskId)))
                .andExpect(status().isNoContent());

        // Verifica que a task está CANCELLED via GET
        mockMvc.perform(withOperatorJwt(
                get("/api/v1/quarantine-tasks/{id}", taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void findAll_byStatus_returnsFilteredTasks() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("FA01");
        UUID productId = createProduct("SKU-QC-FA01");
        UUID noteId = createDivergenceNote(fix, productId);

        // Gera task (→ PENDING)
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated());

        // Lista por status PENDING → deve conter a task criada
        mockMvc.perform(withOperatorJwt(
                get("/api/v1/quarantine-tasks").param("status", "PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].receivingNoteId", hasItem(noteId.toString())));
    }

    @Test
    void decideTask_returnToSupplier_returns200Rejected() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("DC03");
        UUID productId = createProduct("SKU-QC-DC03");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // Start → IN_REVIEW
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/start", taskId)))
                .andExpect(status().isOk());

        // Decide RETURN_TO_SUPPLIER — Kafka mockado, não bloqueia
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/decide", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DecideQuarantineRequest(QuarantineDecision.RETURN_TO_SUPPLIER, "Defeito de fabricação")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision").value("RETURN_TO_SUPPLIER"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void findById_differentTenant_returns404() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("MT01");
        UUID productId = createProduct("SKU-QC-MT01");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // Acesso com tenant diferente → 404 (isolamento multi-tenant, AC11)
        mockMvc.perform(withDifferentTenantJwt(
                get("/api/v1/quarantine-tasks/{id}", taskId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void decideTask_withOperatorRole_returns403() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("RL01");
        UUID productId = createProduct("SKU-QC-RL01");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/start", taskId)))
                .andExpect(status().isOk());

        // OPERATOR não pode decidir (AC12)
        mockMvc.perform(withOperatorJwt(
                patch("/api/v1/quarantine-tasks/{id}/decide", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DecideQuarantineRequest(QuarantineDecision.SCRAP, null)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelTask_withOperatorRole_returns403() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("RL02");
        UUID productId = createProduct("SKU-QC-RL02");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // OPERATOR não pode cancelar (AC12)
        mockMvc.perform(withOperatorJwt(
                patch("/api/v1/quarantine-tasks/{id}/cancel", taskId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelTask_inReviewStatus_returns204() throws Exception {
        QuarantineFixture fix = createQuarantineFixture("CANC02");
        UUID productId = createProduct("SKU-QC-CANC02");
        UUID noteId = createDivergenceNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/quarantine-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // Start → IN_REVIEW, depois cancela (AC9: cancel funciona de PENDING e IN_REVIEW)
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/start", taskId)))
                .andExpect(status().isOk());

        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/quarantine-tasks/{id}/cancel", taskId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(withOperatorJwt(
                get("/api/v1/quarantine-tasks/{id}", taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID readUUID(String json, String field) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return UUID.fromString(node.get(field).asText());
    }

    record QuarantineFixture(UUID warehouseId, UUID dockLocationId, UUID quarantineLocationId, UUID storageLocationId) {}
}
