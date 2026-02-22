package com.odin.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.dto.request.CreateAisleRequest;
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
class AisleControllerIntegrationTest extends AbstractIntegrationTest {

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

    private String createZone(String whCode, String znCode) throws Exception {
        var whReq = new CreateWarehouseRequest(whCode, "WH " + whCode, null);
        var whResult = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(whReq))))
                .andReturn();
        String warehouseId = objectMapper.readTree(whResult.getResponse().getContentAsString()).get("id").asText();

        var znReq = new CreateZoneRequest(znCode, "Zone " + znCode, LocationType.STORAGE);
        var znResult = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(znReq))))
                .andReturn();
        return objectMapper.readTree(znResult.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createAisle_validRequest_returns201() throws Exception {
        String zoneId = createZone("WH-AC-001", "ZN-AC-001");
        var request = new CreateAisleRequest("A-IT-01", "Corredor IT 1");

        mockMvc.perform(withAdminJwt(
                post("/api/v1/zones/{id}/aisles", zoneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("A-IT-01"))
                .andExpect(jsonPath("$.zoneId").value(zoneId));
    }

    @Test
    void createAisle_inactiveZone_returns422() throws Exception {
        String zoneId = createZone("WH-AC-002", "ZN-AC-002");

        // Deactivate zone
        mockMvc.perform(withAdminJwt(
                patch("/api/v1/zones/{id}/deactivate", zoneId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(withAdminJwt(
                post("/api/v1/zones/{id}/aisles", zoneId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAisleRequest("A-INV", null)))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void findAisleById_notFound_returns404() throws Exception {
        mockMvc.perform(withAdminJwt(get("/api/v1/aisles/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
