package com.odin.wms.security;

import com.odin.wms.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests validating the Spring Security JWT resource server configuration.
 *
 * <p>Uses {@code SecurityMockMvcRequestPostProcessors.jwt()} from {@code spring-security-test},
 * which creates a synthetic {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
 * directly in the SecurityContext — bypassing the real {@link org.springframework.security.oauth2.jwt.JwtDecoder}
 * and avoiding any connection to Keycloak in tests.
 */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Unauthenticated access ──────────────────────────────────────────────

    @Test
    void requestWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/nonexistent-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutTokenToApiPathShouldReturn401() throws Exception {
        mockMvc.perform(get("/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    // ── Authenticated access ────────────────────────────────────────────────

    @Test
    void requestWithValidOperatorJwtShouldNotReturn401() throws Exception {
        mockMvc.perform(get("/warehouses")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))
                        .jwt(j -> j
                                .claim("sub", "user-" + UUID.randomUUID())
                                .claim("tenant_id", UUID.randomUUID().toString()))))
                .andExpect(status().is(not(equalTo(401))));  // 404 OK — no handler yet in Wave 1
    }

    @Test
    void requestWithValidAdminJwtShouldNotReturn401() throws Exception {
        mockMvc.perform(get("/some-admin-endpoint")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN"))
                        .jwt(j -> j
                                .claim("sub", "admin-" + UUID.randomUUID())
                                .claim("tenant_id", UUID.randomUUID().toString()))))
                .andExpect(status().is(not(equalTo(401))));
    }

    @Test
    void requestWithValidViewerJwtShouldNotReturn401() throws Exception {
        mockMvc.perform(get("/stock-items")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))
                        .jwt(j -> j
                                .claim("sub", "viewer-" + UUID.randomUUID())
                                .claim("tenant_id", UUID.randomUUID().toString()))))
                .andExpect(status().is(not(equalTo(401))));
    }

    // ── Tenant context ──────────────────────────────────────────────────────

    @Test
    void jwtWithTenantIdClaimShouldNotReturn401() throws Exception {
        UUID tenantId = UUID.randomUUID();

        mockMvc.perform(get("/stock-movements")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR"))
                        .jwt(j -> j
                                .claim("sub", "user-" + UUID.randomUUID())
                                .claim("tenant_id", tenantId.toString())
                                .claim("preferred_username", "john.doe"))))
                .andExpect(status().is(not(equalTo(401))));
    }

    @Test
    void jwtWithoutTenantIdClaimShouldStillNotReturn401() throws Exception {
        // tenant_id is extracted if present, but its absence must not break auth
        mockMvc.perform(get("/locations")
                .with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))
                        .jwt(j -> j.claim("sub", "user-" + UUID.randomUUID()))))
                .andExpect(status().is(not(equalTo(401))));
    }
}
