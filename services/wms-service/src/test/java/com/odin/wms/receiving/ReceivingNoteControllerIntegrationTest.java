package com.odin.wms.receiving;

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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ReceivingNoteControllerIntegrationTest extends AbstractIntegrationTest {

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

    /** Cria WH → Zone → Aisle → Shelf → Location(RECEIVING_DOCK) e retorna os IDs. */
    private TestFixture createDockFixture(String suffix) throws Exception {
        // Warehouse
        String whJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest("WH-RN-" + suffix, "WH " + suffix, null)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID warehouseId = readUUID(whJson, "id");

        // Zone
        String znJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateZoneRequest("ZN-RN-" + suffix, "ZN " + suffix, LocationType.STORAGE)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID zoneId = readUUID(znJson, "id");

        // Aisle
        String aiJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/zones/{id}/aisles", zoneId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAisleRequest("A-RN-" + suffix, "Aisle " + suffix)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID aisleId = readUUID(aiJson, "id");

        // Shelf
        String shJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aisleId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShelfRequest("S-RN-" + suffix, 1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID shelfId = readUUID(shJson, "id");

        // Location (RECEIVING_DOCK)
        String locJson = mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("DOCK-RN-" + suffix, LocationType.RECEIVING_DOCK, null, null)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID dockLocationId = readUUID(locJson, "id");

        return new TestFixture(warehouseId, dockLocationId);
    }

    private UUID createProduct(String sku, boolean controlsLot) throws Exception {
        String json = mockMvc.perform(withAdminJwt(
                post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProductWmsRequest(
                                        UUID.randomUUID(), sku, "Produto " + sku, StorageType.DRY,
                                        controlsLot, false, false,
                                        null, null, null, null, null, null, null, null)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readUUID(json, "id");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void createReceivingNote_validRequest_returns201() throws Exception {
        TestFixture fix = createDockFixture("CR01");
        UUID productId = createProduct("SKU-RN-CR01", false);

        var request = new CreateReceivingNoteRequest(
                fix.warehouseId(), fix.dockLocationId(), null, "PO-CR01",
                List.of(new CreateReceivingNoteItemRequest(productId, 10)));

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.purchaseOrderRef").value("PO-CR01"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void startConference_returns200() throws Exception {
        TestFixture fix = createDockFixture("SC01");
        UUID productId = createProduct("SKU-RN-SC01", false);

        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 5)))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");

        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/start", noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void confirmItem_validWithLot_returns200() throws Exception {
        TestFixture fix = createDockFixture("CI01");
        UUID productId = createProduct("SKU-RN-CI01-LOT", true); // controlsLot = true

        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 8)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");

        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/start", noteId)));

        UUID itemId = readUUID(objectMapper.readTree(noteJson)
                .get("items").get(0).toString(), "id");

        var confirmReq = new ConfirmReceivingItemRequest(
                8, "LOT-001", LocalDate.now(), LocalDate.now().plusYears(1), null, null);

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/items/{itemId}/confirm", noteId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].divergenceType").value("NONE"));
    }

    @Test
    void confirmItem_missingLot_returns422() throws Exception {
        TestFixture fix = createDockFixture("CIM01");
        UUID productId = createProduct("SKU-RN-CIM01", true); // controlsLot = true

        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 5)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");
        UUID itemId = readUUID(objectMapper.readTree(noteJson).get("items").get(0).toString(), "id");

        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/start", noteId)));

        var confirmReq = new ConfirmReceivingItemRequest(5, null, null, null, null, null); // sem lotNumber

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/items/{itemId}/confirm", noteId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void completeNote_allConfirmed_returns200_stockItemCreated() throws Exception {
        TestFixture fix = createDockFixture("COMP01");
        UUID productId = createProduct("SKU-RN-COMP01", false);

        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 10)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");
        UUID itemId = readUUID(objectMapper.readTree(noteJson).get("items").get(0).toString(), "id");

        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/start", noteId)));

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/items/{itemId}/confirm", noteId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmReceivingItemRequest(10, null, null, null, null, null)))));

        mockMvc.perform(withOperatorJwt(post("/api/v1/receiving-notes/{id}/complete", noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].stockItemId").isNotEmpty());
    }

    @Test
    void approveWithOperatorRole_returns403() throws Exception {
        TestFixture fix = createDockFixture("APR01");
        UUID productId = createProduct("SKU-RN-APR01", false);

        // Create a note in COMPLETED_WITH_DIVERGENCE status via full flow
        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 5)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");

        // Operator trying approve-divergences → 403
        mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes/{id}/approve-divergences", noteId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveWithSupervisorRole_returns200() throws Exception {
        TestFixture fix = createDockFixture("APRS01");
        UUID productId = createProduct("SKU-RN-APRS01", false);

        String noteJson = mockMvc.perform(withSupervisorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 5)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");
        UUID itemId = readUUID(objectMapper.readTree(noteJson).get("items").get(0).toString(), "id");

        mockMvc.perform(withSupervisorJwt(post("/api/v1/receiving-notes/{id}/start", noteId)));

        // Confirm with qty=0 → FLAGGED
        mockMvc.perform(withSupervisorJwt(
                post("/api/v1/receiving-notes/{id}/items/{itemId}/confirm", noteId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConfirmReceivingItemRequest(0, null, null, null, null, null)))));

        mockMvc.perform(withSupervisorJwt(post("/api/v1/receiving-notes/{id}/complete", noteId)));

        // Supervisor approves → 200
        mockMvc.perform(withSupervisorJwt(
                post("/api/v1/receiving-notes/{id}/approve-divergences", noteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getNote_crossTenant_returns404() throws Exception {
        TestFixture fix = createDockFixture("CT01");
        UUID productId = createProduct("SKU-RN-CT01", false);

        String noteJson = mockMvc.perform(withOperatorJwt(
                post("/api/v1/receiving-notes").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReceivingNoteRequest(
                                fix.warehouseId(), fix.dockLocationId(), null, null,
                                List.of(new CreateReceivingNoteItemRequest(productId, 3)))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID noteId = readUUID(noteJson, "id");

        UUID otherTenant = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/receiving-notes/{id}", noteId)
                .with(jwt()
                        .jwt(j -> j.claim("tenant_id", otherTenant.toString()).claim("sub", "other"))
                        .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID readUUID(String json, String field) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return UUID.fromString(node.get(field).asText());
    }

    record TestFixture(UUID warehouseId, UUID dockLocationId) {}
}
