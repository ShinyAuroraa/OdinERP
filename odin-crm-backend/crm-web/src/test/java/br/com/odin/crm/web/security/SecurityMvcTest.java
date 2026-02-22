package br.com.odin.crm.web.security;

import br.com.odin.crm.web.rest.MeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityMvcTest {

    @Autowired
    MockMvc mvc;

    // Required to satisfy SecurityConfig constructor injection in @WebMvcTest slice
    @MockBean
    KeycloakJwtAuthenticationConverter keycloakConverter;

    // Required to prevent Spring Security from contacting Keycloak issuer-uri during test context setup
    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/me"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void withValidCrmToken_returns200() throws Exception {
        mvc.perform(get("/api/v1/me")
               .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_crm-admin"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sub").value("user"))
           .andExpect(jsonPath("$.roles[0]").value("crm-admin"));
    }

    @Test
    void withNonCrmToken_returns403() throws Exception {
        mvc.perform(get("/api/v1/me")
               .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_wms-manager"))))
           .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealth_withoutToken_isPublic() throws Exception {
        // @WebMvcTest does not load actuator endpoints, so this returns 404 (no handler).
        // A 404 confirms security did NOT block the request at 401 — permitAll() is active.
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isNotFound());
    }
}
