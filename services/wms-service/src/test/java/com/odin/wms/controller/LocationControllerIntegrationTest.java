package com.odin.wms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.dto.request.CreateAisleRequest;
import com.odin.wms.dto.request.CreateLocationRequest;
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
class LocationControllerIntegrationTest extends AbstractIntegrationTest {

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

    private String createShelf(String suffix) throws Exception {
        var wh = mockMvc.perform(withAdminJwt(
                post("/api/v1/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateWarehouseRequest("WH-LC-" + suffix, "WH " + suffix, null)))))
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
        String aiId = objectMapper.readTree(ai.getResponse().getContentAsString()).get("id").asText();

        var sh = mockMvc.perform(withAdminJwt(
                post("/api/v1/aisles/{id}/shelves", aiId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShelfRequest("S-" + suffix, 1)))))
                .andReturn();
        return objectMapper.readTree(sh.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createLocation_validRequest_returns201WithFullAddress() throws Exception {
        String shelfId = createShelf("LC1");
        var request = new CreateLocationRequest("L-IT-01", LocationType.PICKING, 100, null);

        mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("L-IT-01"))
                .andExpect(jsonPath("$.type").value("PICKING"))
                .andExpect(jsonPath("$.shelfId").value(shelfId))
                .andExpect(jsonPath("$.fullAddress").value(
                        org.hamcrest.Matchers.matchesPattern("WH-LC-LC1/ZN-LC1/A-LC1/S-LC1/L-IT-01")));
    }

    @Test
    void createLocation_duplicateCode_returns422() throws Exception {
        String shelfId = createShelf("LC2");
        String body = objectMapper.writeValueAsString(
                new CreateLocationRequest("L-DUP", LocationType.STORAGE, null, null));

        mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listLocationsByShelf_returnsCorrectLocations() throws Exception {
        String shelfId = createShelf("LC3");

        mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("L-LIST-01", LocationType.STORAGE, null, null)))))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(get("/api/v1/shelves/{id}/locations", shelfId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("L-LIST-01"));
    }

    @Test
    void findByType_returnsFilteredLocations() throws Exception {
        String shelfId = createShelf("LC4");

        mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("L-RD-01", LocationType.RECEIVING_DOCK, null, null)))))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("L-ST-01", LocationType.STORAGE, null, null)))))
                .andExpect(status().isCreated());

        mockMvc.perform(withAdminJwt(get("/api/v1/locations").param("type", "RECEIVING_DOCK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("L-RD-01"));
    }

    @Test
    void deactivateLocation_returns204() throws Exception {
        String shelfId = createShelf("LC5");

        var created = mockMvc.perform(withAdminJwt(
                post("/api/v1/shelves/{id}/locations", shelfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLocationRequest("L-DEACT-01", LocationType.STORAGE, null, null)))))
                .andExpect(status().isCreated())
                .andReturn();
        String locationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(withAdminJwt(patch("/api/v1/locations/{id}/deactivate", locationId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(withAdminJwt(get("/api/v1/locations/{id}", locationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
