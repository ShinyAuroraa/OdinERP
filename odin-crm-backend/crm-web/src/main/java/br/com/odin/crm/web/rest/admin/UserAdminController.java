package br.com.odin.crm.web.rest.admin;

import br.com.odin.crm.infrastructure.user.UserAdminDto;
import br.com.odin.crm.infrastructure.user.UserAdminService;
import br.com.odin.crm.web.rest.admin.dto.UpdateRolesRequest;
import br.com.odin.crm.web.rest.admin.dto.UpdateStatusRequest;
import br.com.odin.crm.web.rest.admin.dto.UserAdminResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de gestão administrativa de usuários.
 * Acessível apenas por crm-admin (AC5).
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('crm-admin')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /** AC1 — Lista todos os usuários do realm com roles e status. */
    @GetMapping("/users")
    public ResponseEntity<List<UserAdminResponse>> listUsers() {
        List<UserAdminResponse> response = userAdminService.listUsers()
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /** AC2 — Atribui/revoga roles por usuário. AC6 — Grava audit log. */
    @PatchMapping("/users/{keycloakId}/roles")
    public ResponseEntity<Void> updateRoles(
            @PathVariable String keycloakId,
            @Valid @RequestBody UpdateRolesRequest request,
            JwtAuthenticationToken auth) {
        UUID actorId = UUID.fromString(auth.getToken().getSubject());
        String actorEmail = auth.getToken().getClaimAsString("email");
        userAdminService.updateRoles(keycloakId, request.roles(), actorId, actorEmail);
        return ResponseEntity.noContent().build();
    }

    /** AC3 — Ativa/inativa usuário (soft no Keycloak). AC6 — Grava audit log. */
    @PatchMapping("/users/{keycloakId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String keycloakId,
            @Valid @RequestBody UpdateStatusRequest request,
            JwtAuthenticationToken auth) {
        UUID actorId = UUID.fromString(auth.getToken().getSubject());
        String actorEmail = auth.getToken().getClaimAsString("email");
        userAdminService.updateStatus(keycloakId, request.enabled(), actorId, actorEmail);
        return ResponseEntity.noContent().build();
    }

    private UserAdminResponse toResponse(UserAdminDto dto) {
        return new UserAdminResponse(
                dto.keycloakId(), dto.nome(), dto.email(), dto.cargo(),
                dto.enabled(), dto.roles(), dto.lastLogin());
    }
}
