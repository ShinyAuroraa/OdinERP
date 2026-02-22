package com.odin.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.dto.request.CreateWarehouseRequest;
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
class WarehouseControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    private MockHttpServletRequestBuilder withAdminJwt(MockHttpServletRequestBuilder request) {
        return request.with(jwt()
                .jwt(j -> j
                        .claim("tenant_id", TEST_TENANT_ID.toString())
                        .claim("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }

    private MockHttpServletRequestBuilder withOperatorJwt(MockHttpServletRequestBuilder request) {
        return request.with(jwt()
                .jwt(j -> j
                        .claim("tenant_id", TEST_TENANT_ID.toString())
                        .claim("sub", "operator-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    @Test
    void createWarehouse_validRequest_returns201() throws Exception {
        var request = new CreateWarehouseRequest("WH-IT-001", "Armazém Integração", "Rua Teste, 1");

        mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WH-IT-001"))
                .andExpect(jsonPath("$.name").value("Armazém Integração"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createWarehouse_duplicateCode_returns422() throws Exception {
        var request = new CreateWarehouseRequest("WH-DUP-001", "Armazém Dup", null);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createWarehouse_withoutJwt_returns401() throws Exception {
        var request = new CreateWarehouseRequest("WH-NOJWT", "Sem JWT", null);

        mockMvc.perform(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWarehouse_withOperatorRole_returns403() throws Exception {
        var request = new CreateWarehouseRequest("WH-403", "Forbidden", null);

        mockMvc.perform(withOperatorJwt(
                post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWarehouses_returnsOnlyTenantWarehouses() throws Exception {
        // Create one warehouse with TEST_TENANT_ID
        var request = new CreateWarehouseRequest("WH-TENANT-LIST", "Tenant List Test", null);
        mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated());

        // Create one with a different tenant
        UUID otherTenant = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/warehouses")
                        .with(jwt()
                                .jwt(j -> j.claim("tenant_id", otherTenant.toString()).claim("sub", "other"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest("WH-OTHER-TENANT", "Other Tenant", null))))
                .andExpect(status().isCreated());

        // List should only contain TEST_TENANT_ID warehouses
        mockMvc.perform(withAdminJwt(get("/api/v1/warehouses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'WH-OTHER-TENANT')]").doesNotExist());
    }
}
