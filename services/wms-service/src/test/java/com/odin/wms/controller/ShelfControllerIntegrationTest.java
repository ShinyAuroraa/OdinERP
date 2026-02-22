package com.odin.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.dto.request.CreateAisleRequest;
import com.odin.wms.dto.request.CreateShelfRequest;
import com.odin.wms.dto.request.CreateWarehouseRequest;
import com.odin.wms.dto.request.CreateZoneRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ShelfControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    private MockHttpServletRequestBuilder withAdminJwt(MockHttpServletRequestBuilder request) {
        return request.with(jwt()
                .jwt(j -> j.claim("tenant_id", TEST_TENANT_ID.toString()).claim("sub", "admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }

    private String createAisle(String suffix) throws Exception {
        var wh = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest("WH-SC-" + suffix, "WH " + suffix, null)))))
                .andReturn();
        String whId = objectMapper.readTree(wh.getResponse().getContentAsString()).get("id").asText();

        var zn = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", whId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateZoneRequest("ZN-" + suffix, "ZN " + suffix, LocationType.STORAGE)))))
                .andReturn();
        String znId = objectMapper.readTree(zn.getResponse().getContentAsString()).get("id").asText();

        var ai = mockMvc.perform(withAdminJwt(
                post("/api/v1/zones/{id}/aisles", znId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAisleRequest("A-" + suffix, "Aisle " + suffix)))))
                .andReturn();
        return objectMapper.readTree(ai.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createShelf_validRequest_returns201() throws Exception {
        String aisleId = createAisle("SH1");
        var request = new CreateShelfRequest("S-IT-01", 2);

        mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aisleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S-IT-01"))
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.aisleId").value(aisleId));
    }

    @Test
    void createShelf_duplicateCode_returns422() throws Exception {
        String aisleId = createAisle("SH2");
        String body = objectMapper.writeValueAsString(new CreateShelfRequest("S-DUP", 1));

        mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aisleId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aisleId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void findShelfById_notFound_returns404() throws Exception {
        mockMvc.perform(withAdminJwt(get("/api/v1/shelves/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
