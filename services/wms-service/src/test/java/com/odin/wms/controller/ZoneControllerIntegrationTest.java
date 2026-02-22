package com.odin.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.dto.request.CreateWarehouseRequest;
import com.odin.wms.dto.request.CreateZoneRequest;
import com.odin.wms.domain.enums.LocationType;
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
class ZoneControllerIntegrationTest extends AbstractIntegrationTest {

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

    private String createWarehouse(String code) throws Exception {
        var wReq = new CreateWarehouseRequest(code, "WH " + code, null);
        var result = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wReq))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createZone_validRequest_returns201() throws Exception {
        String warehouseId = createWarehouse("WH-ZC-001");
        var request = new CreateZoneRequest("ZN-IT-01", "Zona IT 1", LocationType.STORAGE);

        mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ZN-IT-01"))
                .andExpect(jsonPath("$.warehouseId").value(warehouseId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createZone_inactiveWarehouse_returns422() throws Exception {
        String warehouseId = createWarehouse("WH-INACTIVE-Z");

        // Deactivate the warehouse
        mockMvc.perform(withAdminJwt(
                patch("/api/v1/warehouses/{id}/deactivate", warehouseId)))
                .andExpect(status().isNoContent());

        var request = new CreateZoneRequest("ZN-INACTIVE", "Zona Inativa", null);
        mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void findZoneById_otherTenant_returns404() throws Exception {
        String warehouseId = createWarehouse("WH-ZONE-TENANT");
        var request = new CreateZoneRequest("ZN-OTHER-T", "Zona Other Tenant", null);
        var result = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andReturn();
        String zoneId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Access from different tenant
        mockMvc.perform(get("/api/v1/zones/{id}", zoneId)
                        .with(jwt()
                                .jwt(j -> j.claim("tenant_id", UUID.randomUUID().toString()).claim("sub", "other"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listZones_returnsOnlyZonesOfWarehouse() throws Exception {
        String warehouseId = createWarehouse("WH-ZONE-LIST");
        mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses/{id}/zones", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateZoneRequest("ZN-LIST-01", "Zone 1", null)))))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(get("/api/v1/warehouses/{id}/zones", warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ZN-LIST-01"));
    }
}
