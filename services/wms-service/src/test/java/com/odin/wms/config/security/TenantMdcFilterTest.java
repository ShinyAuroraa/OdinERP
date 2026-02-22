package com.odin.wms.config.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantMdcFilter}.
 *
 * <p>The test class is in the same package as {@link TenantMdcFilter}
 * ({@code com.odin.wms.config.security}) to access the {@code protected doFilterInternal}
 * method directly, following the same pattern as {@link TenantContextFilterTest}.
 *
 * <p>Verifies that:
 * <ol>
 *   <li>{@code tenantId} is inserted into the MDC for the duration of the request.</li>
 *   <li>{@code tenantId} is removed from the MDC after the request completes.</li>
 *   <li>A missing tenant context does not insert any key into the MDC.</li>
 *   <li>The MDC is cleared even when the filter chain throws an exception.</li>
 * </ol>
 */
class TenantMdcFilterTest {

    private final TenantMdcFilter filter = new TenantMdcFilter();

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        MDC.clear();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void tenantIdIsInsertedIntoMdcDuringRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);

        AtomicReference<String> mdcValueDuringRequest = new AtomicReference<>();

        FilterChain chain = (req, resp) ->
                mdcValueDuringRequest.set(MDC.get(TenantMdcFilter.MDC_TENANT_ID_KEY));

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                chain
        );

        assertThat(mdcValueDuringRequest.get()).isEqualTo(tenantId.toString());
    }

    @Test
    void tenantIdIsRemovedFromMdcAfterRequest() throws Exception {
        TenantContextHolder.setTenantId(UUID.randomUUID());

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, resp) -> { /* no-op */ }
        );

        assertThat(MDC.get(TenantMdcFilter.MDC_TENANT_ID_KEY)).isNull();
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void missingTenantIdDoesNotInsertIntoMdc() throws Exception {
        TenantContextHolder.clear();

        AtomicReference<String> mdcValueDuringRequest = new AtomicReference<>();

        filter.doFilterInternal(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (req, resp) -> mdcValueDuringRequest.set(MDC.get(TenantMdcFilter.MDC_TENANT_ID_KEY))
        );

        assertThat(mdcValueDuringRequest.get()).isNull();
    }

    @Test
    void mdcIsClearedEvenWhenFilterChainThrows() throws Exception {
        TenantContextHolder.setTenantId(UUID.randomUUID());

        try {
            filter.doFilterInternal(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    (req, resp) -> { throw new RuntimeException("simulated downstream error"); }
            );
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get(TenantMdcFilter.MDC_TENANT_ID_KEY)).isNull();
    }
}
