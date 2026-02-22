package com.odin.wms.putaway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.dto.request.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PutawayControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

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

    /**
     * Cria WH → Zone → Aisle → Shelf → DOCK + STORAGE locations.
     * O STORAGE é necessário para o motor de sugestão de putaway.
     */
    private PutawayFixture createPutawayFixture(String suffix) throws Exception {
        // Warehouse
        String whJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest("WH-PT-" + suffix, "WH PT " + suffix, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID warehouseId = readUUID(whJson, "id");

        // Zone
        String znJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateZoneRequest("ZN-PT-" + suffix, "ZN PT", LocationType.STORAGE)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID zoneId = readUUID(znJson, "id");

        // Aisle
        String aiJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/zones/{id}/aisles", zoneId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAisleRequest("A-PT-" + suffix, "Aisle PT")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID aisleId = readUUID(aiJson, "id");

        // Shelf
        String shJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aisleId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShelfRequest("S-PT-" + suffix, 1)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID shelfId = readUUID(shJson, "id");

        // RECEIVING_DOCK location
        String dockJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("DOCK-PT-" + suffix, LocationType.RECEIVING_DOCK, null, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID dockLocationId = readUUID(dockJson, "id");

        // STORAGE location (target for putaway suggestion)
        String storJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("STOR-PT-" + suffix, LocationType.STORAGE, null, null)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID storageLocationId = readUUID(storJson, "id");

        return new PutawayFixture(warehouseId, dockLocationId, storageLocationId);
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
     * Cria nota → inicia → confirma item → completa. Retorna o noteId.
     */
    private UUID createAndCompleteReceivingNote(PutawayFixture fix, UUID productId) throws Exception {
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

        // Confirm item
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/items/{itemId}/confirm", noteId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmReceivingItemRequest(5, null, null, null, null, null)))))
                .andExpect(status().isOk());

        // Complete
        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/complete", noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        return noteId;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void generateTasks_afterCompletedNote_returns201WithSuggestedLocation() throws Exception {
        PutawayFixture fix = createPutawayFixture("GT01");
        UUID productId = createProduct("SKU-PT-GT01");
        UUID noteId = createAndCompleteReceivingNote(fix, productId);

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].strategyUsed").value("FIFO"))
                .andExpect(jsonPath("$[0].suggestedLocationId").value(fix.storageLocationId().toString()))
                .andExpect(jsonPath("$[0].sourceLocationId").value(fix.dockLocationId().toString()));
    }

    @Test
    void generateTasks_duplicateCall_returns409() throws Exception {
        PutawayFixture fix = createPutawayFixture("GT02");
        UUID productId = createProduct("SKU-PT-GT02");
        UUID noteId = createAndCompleteReceivingNote(fix, productId);

        // Primeira chamada — sucesso
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isCreated());

        // Segunda chamada — conflito
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isConflict());
    }

    @Test
    void startTask_validPendingTask_returns200InProgress() throws Exception {
        PutawayFixture fix = createPutawayFixture("ST01");
        UUID productId = createProduct("SKU-PT-ST01");
        UUID noteId = createAndCompleteReceivingNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        mockMvc.perform(withOperatorJwt(
                patch("/api/v1/putaway-tasks/{id}/start", taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
    }

    @Test
    void confirmTask_useSuggestedLocation_returns200Confirmed() throws Exception {
        PutawayFixture fix = createPutawayFixture("CT01");
        UUID productId = createProduct("SKU-PT-CT01");
        UUID noteId = createAndCompleteReceivingNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // Start
        mockMvc.perform(withOperatorJwt(
                patch("/api/v1/putaway-tasks/{id}/start", taskId)))
                .andExpect(status().isOk());

        // Confirm (body vazio → usa localização sugerida)
        mockMvc.perform(withOperatorJwt(
                patch("/api/v1/putaway-tasks/{id}/confirm", taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedLocationId").value(fix.storageLocationId().toString()))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());
    }

    @Test
    void cancelTask_withOperatorRole_returns403() throws Exception {
        PutawayFixture fix = createPutawayFixture("CANC01");
        UUID productId = createProduct("SKU-PT-CANC01");
        UUID noteId = createAndCompleteReceivingNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // OPERATOR não tem autorização para cancelar
        mockMvc.perform(withOperatorJwt(
                patch("/api/v1/putaway-tasks/{id}/cancel", taskId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelTask_withSupervisorRole_returns204NoContent() throws Exception {
        PutawayFixture fix = createPutawayFixture("CANC02");
        UUID productId = createProduct("SKU-PT-CANC02");
        UUID noteId = createAndCompleteReceivingNote(fix, productId);

        String tasksJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID taskId = readUUID(objectMapper.readTree(tasksJson).get(0).toString(), "id");

        // SUPERVISOR pode cancelar
        mockMvc.perform(withSupervisorJwt(
                patch("/api/v1/putaway-tasks/{id}/cancel", taskId)))
                .andExpect(status().isNoContent());

        // Verifica que está CANCELLED via GET
        mockMvc.perform(withOperatorJwt(
                get("/api/v1/putaway-tasks/{id}", taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void findAll_byStatus_returnsFilteredTasks() throws Exception {
        PutawayFixture fix = createPutawayFixture("FA01");
        UUID productId = createProduct("SKU-PT-FA01");
        UUID noteId = createAndCompleteReceivingNote(fix, productId);

        // Gera task (PENDING)
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/putaway-tasks", noteId)))
                .andExpect(status().isCreated());

        // Lista por status PENDING → deve retornar a task criada
        mockMvc.perform(withOperatorJwt(
                get("/api/v1/putaway-tasks").param("status", "PENDING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].receivingNoteId", hasItem(noteId.toString())));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID readUUID(String json, String field) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return UUID.fromString(node.get(field).asText());
    }

    record PutawayFixture(UUID warehouseId, UUID dockLocationId, UUID storageLocationId) {}
}
