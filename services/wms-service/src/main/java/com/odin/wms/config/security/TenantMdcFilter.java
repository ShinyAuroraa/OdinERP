package com.odin.wms.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates the SLF4J MDC with the {@code tenantId} extracted
 * from {@link TenantContextHolder}.
 *
 * <p>Must be registered <em>after</em> {@link TenantContextFilter} in the Spring Security
 * filter chain so that {@code TenantContextHolder} already contains the tenant UUID when
 * this filter executes.
 *
 * <p>The MDC key {@code tenantId} is removed in the {@code finally} block to prevent
 * context leaks across thread-pool reuse.
 *
 * <p>NOT annotated with {@code @Component} — registered explicitly in
 * {@code SecurityConfig} to avoid double-registration as a Servlet filter.
 */
public class TenantMdcFilter extends OncePerRequestFilter {

    static final String MDC_TENANT_ID_KEY = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        UUID tenantId = TenantContextHolder.getTenantId();
        try {
            if (tenantId != null) {
                MDC.put(MDC_TENANT_ID_KEY, tenantId.toString());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT_ID_KEY);
        }
    }
}
