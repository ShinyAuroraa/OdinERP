package com.odin.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.dto.request.CreateProductWmsRequest;
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
class ProductWmsControllerIntegrationTest extends AbstractIntegrationTest {

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

    private UUID createProduct(String sku, StorageType storageType) throws Exception {
        var request = new CreateProductWmsRequest(
                UUID.randomUUID(), sku, "Produto " + sku, storageType,
                false, false, false, null, null, null, null, null, null, null, null);
        String response = mockMvc.perform(withAdminJwt(
                        post("/api/v1/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").traverse(objectMapper).readValueAs(UUID.class);
    }

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        var request = new CreateProductWmsRequest(
                UUID.randomUUID(), "SKU-IT-001", "Produto Integração", StorageType.DRY,
                true, false, false, "1234567890128", null, null, null, null, null, null, null);

        mockMvc.perform(withAdminJwt(
                        post("/api/v1/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-IT-001"))
                .andExpect(jsonPath("$.storageType").value("DRY"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.controlsLot").value(true))
                .andExpect(jsonPath("$.ean13").value("1234567890128"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createProduct_duplicateSku_returns422() throws Exception {
        var request = new CreateProductWmsRequest(
                UUID.randomUUID(), "SKU-DUP-001", "Produto Dup", StorageType.DRY,
                false, false, false, null, null, null, null, null, null, null, null);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(withAdminJwt(
                        post("/api/v1/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(
                        post("/api/v1/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void getProduct_returns200() throws Exception {
        UUID id = createProduct("SKU-GET-001", StorageType.FROZEN);

        mockMvc.perform(withAdminJwt(get("/api/v1/products/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-GET-001"))
                .andExpect(jsonPath("$.storageType").value("FROZEN"));
    }

    @Test
    void getProduct_crossTenant_returns404() throws Exception {
        UUID id = createProduct("SKU-CROSS-001", StorageType.DRY);
        UUID otherTenant = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/products/" + id)
                        .with(jwt()
                                .jwt(j -> j
                                        .claim("tenant_id", otherTenant.toString())
                                        .claim("sub", "other-user"))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProducts_filterByStorageType_returnsFiltered() throws Exception {
        createProduct("SKU-FILTER-DRY", StorageType.DRY);
        createProduct("SKU-FILTER-REF", StorageType.REFRIGERATED);

        mockMvc.perform(withAdminJwt(get("/api/v1/products?storageType=REFRIGERATED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == 'SKU-FILTER-REF')]").exists())
                .andExpect(jsonPath("$[?(@.sku == 'SKU-FILTER-DRY')]").doesNotExist());
    }

    @Test
    void deactivateProduct_returns204() throws Exception {
        UUID id = createProduct("SKU-DEA-001", StorageType.DRY);

        mockMvc.perform(withAdminJwt(delete("/api/v1/products/" + id)))
                .andExpect(status().isNoContent());

        mockMvc.perform(withAdminJwt(get("/api/v1/products/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
