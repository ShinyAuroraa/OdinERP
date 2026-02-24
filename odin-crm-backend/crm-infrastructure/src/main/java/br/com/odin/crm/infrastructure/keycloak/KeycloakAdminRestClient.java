package br.com.odin.crm.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP para o Keycloak Admin REST API.
 *
 * <p>Autentica via client_credentials com service account e mantém
 * o token em cache até 60 segundos antes do vencimento.
 *
 * <p>Endpoints usados:
 * <ul>
 *   <li>POST /realms/master/protocol/openid-connect/token — client_credentials</li>
 *   <li>GET  /admin/realms/{realm}/users</li>
 *   <li>GET  /admin/realms/{realm}/roles</li>
 *   <li>GET  /admin/realms/{realm}/users/{id}/role-mappings/realm</li>
 *   <li>POST /admin/realms/{realm}/users/{id}/role-mappings/realm</li>
 *   <li>DELETE /admin/realms/{realm}/users/{id}/role-mappings/realm</li>
 *   <li>PUT  /admin/realms/{realm}/users/{id}</li>
 * </ul>
 */
@Component
public class KeycloakAdminRestClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminRestClient.class);

    private final RestClient restClient;
    private final KeycloakAdminProperties props;

    // Token cache — volatile para visibilidade entre threads
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;
    private final Object tokenLock = new Object();

    public KeycloakAdminRestClient(KeycloakAdminProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
    }

    // -------------------------------------------------------------------------
    // DTOs públicos (usados por UserAdminService)
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakUserDto(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            Long createdTimestamp,
            Map<String, List<String>> attributes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleRepresentation(String id, String name) {}

    // -------------------------------------------------------------------------
    // API Methods
    // -------------------------------------------------------------------------

    public List<KeycloakUserDto> listUsers() {
        String token = getToken();
        String url = adminUrl("/users?max=200");
        return restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<KeycloakUserDto>>() {});
    }

    public List<RoleRepresentation> getRealmRoles() {
        String token = getToken();
        String url = adminUrl("/roles");
        List<RoleRepresentation> all = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoleRepresentation>>() {});
        if (all == null) return List.of();
        return all.stream().filter(r -> r.name().startsWith("crm-")).toList();
    }

    public List<String> getUserRoles(String userId) {
        String token = getToken();
        String url = adminUrl("/users/" + userId + "/role-mappings/realm");
        List<RoleRepresentation> roles = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoleRepresentation>>() {});
        if (roles == null) return List.of();
        return roles.stream()
                .map(RoleRepresentation::name)
                .filter(name -> name.startsWith("crm-"))
                .toList();
    }

    public void assignRoles(String userId, List<RoleRepresentation> roles) {
        if (roles.isEmpty()) return;
        String token = getToken();
        String url = adminUrl("/users/" + userId + "/role-mappings/realm");
        restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity();
    }

    public void removeRoles(String userId, List<RoleRepresentation> roles) {
        if (roles.isEmpty()) return;
        String token = getToken();
        String url = adminUrl("/users/" + userId + "/role-mappings/realm");
        // DELETE with body — Keycloak Admin API requires role representations in body.
        // RestClient.delete() does not support body; use method(HttpMethod.DELETE) instead.
        restClient.method(HttpMethod.DELETE)
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(roles)
                .retrieve()
                .toBodilessEntity();
    }

    public void setUserEnabled(String userId, boolean enabled) {
        String token = getToken();
        String url = adminUrl("/users/" + userId);
        restClient.put()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .toBodilessEntity();
    }

    // -------------------------------------------------------------------------
    // Token management
    // -------------------------------------------------------------------------

    String getToken() {
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }
            log.debug("Fetching new Keycloak Admin token for client '{}'", props.getClientId());
            String body = "grant_type=client_credentials"
                    + "&client_id=" + encode(props.getClientId())
                    + "&client_secret=" + encode(props.getClientSecret());

            TokenResponse resp = restClient.post()
                    .uri(props.getUrl() + "/realms/master/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(TokenResponse.class);

            if (resp == null || resp.accessToken() == null) {
                throw new IllegalStateException("Failed to obtain Keycloak Admin token");
            }
            cachedToken = resp.accessToken();
            tokenExpiry = Instant.now().plusSeconds(Math.max(resp.expiresIn() - 60L, 30L));
            return cachedToken;
        }
    }

    private String adminUrl(String path) {
        return props.getUrl() + "/admin/realms/" + props.getRealm() + path;
    }

    private static String encode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    // -------------------------------------------------------------------------
    // Internal DTOs
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {}
}
