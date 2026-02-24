package br.com.odin.crm.web.rest.admin;

import br.com.odin.crm.infrastructure.user.UserAdminDto;
import br.com.odin.crm.infrastructure.user.UserAdminService;
import br.com.odin.crm.web.security.KeycloakJwtAuthenticationConverter;
import br.com.odin.crm.web.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UserAdminControllerTest {

    static final String ACTOR_UUID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    MockMvc mvc;

    @MockBean
    KeycloakJwtAuthenticationConverter keycloakConverter;

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    UserAdminService userAdminService;

    // -------------------------------------------------------------------------
    // AC5 — apenas crm-admin acessa
    // -------------------------------------------------------------------------

    @Test
    void listUsers_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withNonAdminRole_returns403() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
               .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_crm-vendedor"))))
           .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_withCrmAdmin_returns200() throws Exception {
        given(userAdminService.listUsers()).willReturn(List.of(
                new UserAdminDto("uuid-1", "João Silva", "joao@test.com", null, true,
                        List.of("crm-vendedor"), null)
        ));

        mvc.perform(get("/api/v1/admin/users")
               .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_crm-admin"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].keycloakId").value("uuid-1"))
           .andExpect(jsonPath("$[0].nome").value("João Silva"))
           .andExpect(jsonPath("$[0].roles[0]").value("crm-vendedor"));
    }

    // -------------------------------------------------------------------------
    // AC2 — atribuição de roles
    // -------------------------------------------------------------------------

    @Test
    void updateRoles_withCrmAdmin_returns204() throws Exception {
        mvc.perform(patch("/api/v1/admin/users/kc-uuid/roles")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"roles\":[\"crm-vendedor\",\"crm-gerente\"]}")
               .with(jwt()
                       .authorities(new SimpleGrantedAuthority("ROLE_crm-admin"))
                       .jwt(j -> j.subject(ACTOR_UUID).claim("email", "admin@test.com"))))
           .andExpect(status().isNoContent());

        verify(userAdminService).updateRoles(
                "kc-uuid",
                List.of("crm-vendedor", "crm-gerente"),
                java.util.UUID.fromString(ACTOR_UUID),
                "admin@test.com");
    }

    @Test
    void updateRoles_withNonAdminRole_returns403() throws Exception {
        mvc.perform(patch("/api/v1/admin/users/kc-uuid/roles")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"roles\":[\"crm-vendedor\"]}")
               .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_crm-gerente"))))
           .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // AC3 — ativação/inativação
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_disable_withCrmAdmin_returns204() throws Exception {
        mvc.perform(patch("/api/v1/admin/users/kc-uuid/status")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"enabled\":false}")
               .with(jwt()
                       .authorities(new SimpleGrantedAuthority("ROLE_crm-admin"))
                       .jwt(j -> j.subject(ACTOR_UUID).claim("email", "admin@test.com"))))
           .andExpect(status().isNoContent());

        verify(userAdminService).updateStatus(
                "kc-uuid",
                false,
                java.util.UUID.fromString(ACTOR_UUID),
                "admin@test.com");
    }
}
