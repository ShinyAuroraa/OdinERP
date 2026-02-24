package br.com.odin.crm.infrastructure.user;

import br.com.odin.crm.infrastructure.keycloak.KeycloakAdminRestClient;
import br.com.odin.crm.infrastructure.keycloak.KeycloakAdminRestClient.KeycloakUserDto;
import br.com.odin.crm.infrastructure.keycloak.KeycloakAdminRestClient.RoleRepresentation;
import br.com.odin.crm.infrastructure.persistence.audit.AuditLogEntity;
import br.com.odin.crm.infrastructure.persistence.audit.AuditLogJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Orquestra operações de gestão de usuários via Keycloak Admin REST API,
 * gravando audit log antes de cada ação.
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final String ENTITY_TYPE = "USER";

    private final KeycloakAdminRestClient keycloakAdmin;
    private final AuditLogJpaRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public UserAdminService(KeycloakAdminRestClient keycloakAdmin,
                            AuditLogJpaRepository auditLogRepository,
                            ObjectMapper objectMapper) {
        this.keycloakAdmin = keycloakAdmin;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public List<UserAdminDto> listUsers() {
        List<KeycloakUserDto> users = keycloakAdmin.listUsers();
        return users.stream().map(u -> {
            List<String> roles = keycloakAdmin.getUserRoles(u.id());
            String lastLogin = u.createdTimestamp() != null
                    ? Instant.ofEpochMilli(u.createdTimestamp()).toString()
                    : null;
            String nome = buildNome(u.firstName(), u.lastName(), u.username());
            return new UserAdminDto(u.id(), nome, u.email(), null, u.enabled(), roles, lastLogin);
        }).toList();
    }

    public void updateRoles(String keycloakId, List<String> newRoles,
                            UUID actorId, String actorEmail) {
        List<String> currentRoles = keycloakAdmin.getUserRoles(keycloakId);
        List<RoleRepresentation> allRealmRoles = keycloakAdmin.getRealmRoles();

        Map<String, RoleRepresentation> rolesByName = allRealmRoles.stream()
                .collect(java.util.stream.Collectors.toMap(RoleRepresentation::name, r -> r));

        List<RoleRepresentation> toAdd = newRoles.stream()
                .filter(r -> !currentRoles.contains(r))
                .map(rolesByName::get)
                .filter(Objects::nonNull)
                .toList();

        List<RoleRepresentation> toRemove = currentRoles.stream()
                .filter(r -> !newRoles.contains(r))
                .map(rolesByName::get)
                .filter(Objects::nonNull)
                .toList();

        if (toAdd.isEmpty() && toRemove.isEmpty()) return;

        String action = !toAdd.isEmpty() ? "ROLE_ASSIGNED" : "ROLE_REVOKED";

        saveAuditLog(ENTITY_TYPE, parseKeycloakUuid(keycloakId), action,
                actorId, actorEmail,
                Map.of("roles", currentRoles),
                Map.of("roles", newRoles));

        keycloakAdmin.assignRoles(keycloakId, toAdd);
        keycloakAdmin.removeRoles(keycloakId, toRemove);
    }

    public void updateStatus(String keycloakId, boolean enabled,
                             UUID actorId, String actorEmail) {
        String action = enabled ? "USER_ENABLED" : "USER_DISABLED";

        saveAuditLog(ENTITY_TYPE, parseKeycloakUuid(keycloakId), action,
                actorId, actorEmail,
                Map.of("enabled", !enabled),
                Map.of("enabled", enabled));

        keycloakAdmin.setUserEnabled(keycloakId, enabled);
    }

    // -------------------------------------------------------------------------

    private void saveAuditLog(String entityType, UUID entityId, String action,
                               UUID actorId, String actorEmail,
                               Object oldValues, Object newValues) {
        try {
            AuditLogEntity entry = AuditLogEntity.of(
                    entityType, entityId, action, actorId, actorEmail,
                    objectMapper.writeValueAsString(oldValues),
                    objectMapper.writeValueAsString(newValues));
            auditLogRepository.save(entry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit log values: {}", e.getMessage());
        }
    }

    private UUID parseKeycloakUuid(String keycloakId) {
        try {
            return UUID.fromString(keycloakId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(keycloakId.getBytes());
        }
    }

    private String buildNome(String firstName, String lastName, String username) {
        if (firstName == null && lastName == null) return username != null ? username : "";
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }
}
