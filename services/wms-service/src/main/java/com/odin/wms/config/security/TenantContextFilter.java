package com.odin.wms.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that extracts the {@code tenant_id} claim from the authenticated JWT
 * and stores it in {@link TenantContextHolder} for the duration of the request.
 *
 * <p>Must run AFTER {@code BearerTokenAuthenticationFilter} so that the
 * {@link JwtAuthenticationToken} is already present in the {@link SecurityContextHolder}.
 * Registered via {@code SecurityConfig.addFilterAfter(...)}.
 *
 * <p><strong>NOT a {@code @Component}</strong>: registered manually in
 * {@link com.odin.wms.config.SecurityConfig} to prevent double-registration
 * as both a Servlet filter and a Spring Security filter.
 *
 * <p>The {@code finally} block guarantees {@link TenantContextHolder#clear()} is called
 * even when the downstream filter chain throws an exception, preventing ThreadLocal
 * leaks in thread-pool environments.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String tenantIdStr = jwtAuth.getToken().getClaimAsString(TENANT_ID_CLAIM);
                if (tenantIdStr != null && !tenantIdStr.isBlank()) {
                    TenantContextHolder.setTenantId(UUID.fromString(tenantIdStr));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
