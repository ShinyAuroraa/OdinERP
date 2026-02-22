package com.odin.wms.config.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantContextFilter}.
 *
 * <p>Verifies that:
 * <ol>
 *   <li>The {@code tenant_id} JWT claim is extracted and stored in {@link TenantContextHolder}
 *       during the request.</li>
 *   <li>The holder is cleared in the {@code finally} block after the filter chain completes.</li>
 *   <li>Missing or null {@code tenant_id} claims are handled gracefully.</li>
 * </ol>
 *
 * <p>The test class is intentionally placed in the same package as {@link TenantContextFilter}
 * ({@code com.odin.wms.config.security}) to access the {@code protected doFilterInternal} method.
 */
class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    void tenantIdIsExtractedFromJwtClaimDuringRequest() throws Exception {
        UUID expectedTenantId = UUID.randomUUID();
        setupSecurityContextWithTenantId(expectedTenantId.toString());

        AtomicReference<UUID> capturedTenantId = new AtomicReference<>();

        FilterChain chain = (req, resp) ->
                capturedTenantId.set(TenantContextHolder.getTenantId());

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                chain
        );

        assertThat(capturedTenantId.get()).isEqualTo(expectedTenantId);
    }

    @Test
    void tenantIdIsClearedAfterRequestCompletes() throws Exception {
        setupSecurityContextWithTenantId(UUID.randomUUID().toString());

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, resp) -> { /* no-op */ }
        );

        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void tenantIdIsClearedEvenWhenFilterChainThrows() throws Exception {
        setupSecurityContextWithTenantId(UUID.randomUUID().toString());

        try {
            filter.doFilterInternal(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    (req, resp) -> { throw new RuntimeException("simulated downstream error"); }
            );
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    void missingTenantIdClaimDoesNotPopulateHolder() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<UUID> capturedTenantId = new AtomicReference<>();

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, resp) -> capturedTenantId.set(TenantContextHolder.getTenantId())
        );

        assertThat(capturedTenantId.get()).isNull();
    }

    @Test
    void noAuthenticationInContextDoesNotPopulateHolder() throws Exception {
        // SecurityContext has no authentication (e.g. /actuator/health)
        SecurityContextHolder.clearContext();

        AtomicReference<UUID> capturedTenantId = new AtomicReference<>();

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, resp) -> capturedTenantId.set(TenantContextHolder.getTenantId())
        );

        assertThat(capturedTenantId.get()).isNull();
    }

    @Test
    void nonJwtAuthenticationDoesNotPopulateHolder() throws Exception {
        // Non-JWT authentication (UsernamePasswordAuthenticationToken, etc.)
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", "password", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicReference<UUID> capturedTenantId = new AtomicReference<>();

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, resp) -> capturedTenantId.set(TenantContextHolder.getTenantId())
        );

        assertThat(capturedTenantId.get()).isNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setupSecurityContextWithTenantId(String tenantIdStr) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("tenant_id", tenantIdStr)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
