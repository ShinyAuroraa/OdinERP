package com.odin.wms.security;

import com.odin.wms.config.security.KeycloakJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KeycloakJwtAuthenticationConverter}.
 *
 * <p>Verifies that roles are correctly extracted from the Keycloak JWT structure
 * and converted to Spring Security {@link GrantedAuthority} instances.
 *
 * <p>JWT structure used:
 * <pre>
 * "realm_access":    { "roles": [...] }          — realm-level roles
 * "resource_access": { "wms-service": { "roles": [...] } }   — client-level roles
 * </pre>
 */
class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter =
            new KeycloakJwtAuthenticationConverter();

    // ── realm_access.roles ──────────────────────────────────────────────────

    @Test
    void rolesExtractedFromRealmAccessClaim() {
        // realm_access = { "roles": ["wms-admin", "offline_access"] }
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("wms-admin", "offline_access")),
                Map.of()
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token)).contains("ROLE_WMS_ADMIN");
    }

    @Test
    void allFourWmsRolesAreExtractedFromRealmAccess() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("wms-admin", "wms-supervisor", "wms-operator", "wms-viewer")),
                Map.of()
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token))
                .contains("ROLE_WMS_ADMIN", "ROLE_WMS_SUPERVISOR", "ROLE_WMS_OPERATOR", "ROLE_WMS_VIEWER");
    }

    // ── resource_access.wms-service.roles ───────────────────────────────────

    @Test
    void rolesExtractedFromResourceAccessClaim() {
        // resource_access = { "wms-service": { "roles": ["wms-operator"] } }
        Jwt jwt = buildJwt(
                Map.of(),
                Map.of("wms-service", Map.of("roles", List.of("wms-operator")))
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token)).contains("ROLE_WMS_OPERATOR");
    }

    @Test
    void rolesFromOtherClientsAreIgnored() {
        Jwt jwt = buildJwt(
                Map.of(),
                Map.of(
                        "other-service", Map.of("roles", List.of("some-role")),
                        "wms-service",   Map.of("roles", List.of("wms-viewer"))
                )
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token))
                .contains("ROLE_WMS_VIEWER")
                .doesNotContain("ROLE_SOME_ROLE");
    }

    // ── Both claims merged ───────────────────────────────────────────────────

    @Test
    void rolesFromBothClaimsAreMerged() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("wms-supervisor")),
                Map.of("wms-service", Map.of("roles", List.of("wms-operator")))
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token))
                .contains("ROLE_WMS_SUPERVISOR", "ROLE_WMS_OPERATOR");
    }

    @Test
    void duplicateRolesAreDeduplicatedWhenPresentInBothClaims() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("wms-operator")),
                Map.of("wms-service", Map.of("roles", List.of("wms-operator")))
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        long count = token.getAuthorities().stream()
                .filter(a -> a.getAuthority().equals("ROLE_WMS_OPERATOR"))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ── Role naming convention ───────────────────────────────────────────────

    @Test
    void roleHyphensAreReplacedWithUnderscores() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("wms-admin")),
                Map.of()
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token))
                .contains("ROLE_WMS_ADMIN")
                .doesNotContain("ROLE_WMS-ADMIN");
    }

    @Test
    void rolesAreUppercased() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("wms-operator")),
                Map.of()
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token)).contains("ROLE_WMS_OPERATOR");
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    void missingRealmAccessClaimReturnsEmptyAuthorities() {
        Jwt jwt = buildJwt(Map.of(), Map.of());

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void missingResourceAccessForWmsClientIsHandledGracefully() {
        Jwt jwt = buildJwt(
                Map.of("roles", List.of("wms-viewer")),
                Map.of("account", Map.of("roles", List.of("manage-account")))
        );

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityNames(token)).contains("ROLE_WMS_VIEWER");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * @param realmAccessValue    the VALUE of the "realm_access" claim, e.g. {@code {"roles": [...]}}
     * @param resourceAccessValue the VALUE of the "resource_access" claim, e.g. {@code {"wms-service": {"roles": [...]}}}
     */
    private Jwt buildJwt(Map<String, Object> realmAccessValue, Map<String, Object> resourceAccessValue) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (!realmAccessValue.isEmpty()) {
            builder.claim("realm_access", realmAccessValue);
        }
        if (!resourceAccessValue.isEmpty()) {
            builder.claim("resource_access", resourceAccessValue);
        }

        return builder.build();
    }

    private List<String> authorityNames(AbstractAuthenticationToken token) {
        Collection<GrantedAuthority> authorities = token.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
