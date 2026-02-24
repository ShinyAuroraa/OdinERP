package br.com.odin.crm.infrastructure.user;

import br.com.odin.crm.infrastructure.keycloak.KeycloakAdminRestClient;
import br.com.odin.crm.infrastructure.keycloak.KeycloakAdminRestClient.KeycloakUserDto;
import br.com.odin.crm.infrastructure.keycloak.KeycloakAdminRestClient.RoleRepresentation;
import br.com.odin.crm.infrastructure.persistence.audit.AuditLogEntity;
import br.com.odin.crm.infrastructure.persistence.audit.AuditLogJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserAdminServiceTest {

    @Mock KeycloakAdminRestClient keycloakAdmin;
    @Mock AuditLogJpaRepository auditLogRepository;

    UserAdminService service;

    final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    final String ACTOR_EMAIL = "admin@test.com";
    final String KC_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UserAdminService(keycloakAdmin, auditLogRepository, new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // listUsers
    // -------------------------------------------------------------------------

    @Test
    void listUsers_returnsMappedDtos() {
        given(keycloakAdmin.listUsers()).willReturn(List.of(
                new KeycloakUserDto("id-1", "jsilva", "j@test.com", "João", "Silva", true, null, null)
        ));
        given(keycloakAdmin.getUserRoles("id-1")).willReturn(List.of("crm-vendedor"));

        List<UserAdminDto> result = service.listUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("João Silva");
        assertThat(result.get(0).roles()).containsExactly("crm-vendedor");
    }

    // -------------------------------------------------------------------------
    // updateStatus — AC3 + AC6
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_disable_savesAuditLogThenCallsKeycloak() {
        service.updateStatus(KC_ID, false, ACTOR_ID, ACTOR_EMAIL);

        // AC6 — audit log gravado
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntity log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("USER_DISABLED");
        assertThat(log.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(log.getActorEmail()).isEqualTo(ACTOR_EMAIL);
        assertThat(log.getOldValues()).contains("true");
        assertThat(log.getNewValues()).contains("false");

        // AC4 — ação no Keycloak Admin API
        verify(keycloakAdmin).setUserEnabled(KC_ID, false);
    }

    @Test
    void updateStatus_enable_setsActionUserEnabled() {
        service.updateStatus(KC_ID, true, ACTOR_ID, ACTOR_EMAIL);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("USER_ENABLED");
        verify(keycloakAdmin).setUserEnabled(KC_ID, true);
    }

    // -------------------------------------------------------------------------
    // updateRoles — AC2 + AC6
    // -------------------------------------------------------------------------

    @Test
    void updateRoles_addsAndRemoves_savesAuditLog() {
        given(keycloakAdmin.getUserRoles(KC_ID)).willReturn(List.of("crm-vendedor"));
        given(keycloakAdmin.getRealmRoles()).willReturn(List.of(
                new RoleRepresentation("r1", "crm-vendedor"),
                new RoleRepresentation("r2", "crm-gerente")
        ));

        service.updateRoles(KC_ID, List.of("crm-gerente"), ACTOR_ID, ACTOR_EMAIL);

        // AC6 — audit log gravado
        verify(auditLogRepository).save(any(AuditLogEntity.class));

        // AC4 — add crm-gerente, remove crm-vendedor
        verify(keycloakAdmin).assignRoles(eq(KC_ID), any());
        verify(keycloakAdmin).removeRoles(eq(KC_ID), any());
    }

    @Test
    void updateRoles_noChange_skipsKeycloakAndAuditLog() {
        given(keycloakAdmin.getUserRoles(KC_ID)).willReturn(List.of("crm-vendedor"));
        given(keycloakAdmin.getRealmRoles()).willReturn(List.of(
                new RoleRepresentation("r1", "crm-vendedor")
        ));

        service.updateRoles(KC_ID, List.of("crm-vendedor"), ACTOR_ID, ACTOR_EMAIL);

        verify(auditLogRepository, never()).save(any());
        verify(keycloakAdmin, never()).assignRoles(any(), any());
        verify(keycloakAdmin, never()).removeRoles(any(), any());
    }
}
