package com.odin.wms.config;

import com.odin.wms.config.security.KeycloakJwtAuthenticationConverter;
import com.odin.wms.config.security.TenantContextFilter;
import com.odin.wms.config.security.TenantMdcFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for the WMS microservice.
 *
 * <p>Configures the service as an OAuth2 JWT Resource Server authenticated by Keycloak.
 * Replaces the temporary permissive configuration from Story 1.1.
 *
 * <p><strong>Security model:</strong>
 * <ul>
 *   <li>{@code /actuator/health/**} — public (K8s liveness/readiness probes)</li>
 *   <li>{@code /actuator/**} — public (management port is behind internal network via Istio)</li>
 *   <li>All other endpoints — require a valid Keycloak JWT ({@code authenticated()})</li>
 *   <li>Role-based access on specific endpoints uses {@code @PreAuthorize} in Wave 2+ controllers</li>
 * </ul>
 *
 * <p>The {@link TenantContextFilter} is added AFTER {@link BearerTokenAuthenticationFilter}
 * so it can read the authenticated {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
 * to extract and populate the {@code tenant_id} into {@link com.odin.wms.config.security.TenantContextHolder}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakConverter;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(KeycloakJwtAuthenticationConverter keycloakConverter) {
        this.keycloakConverter = keycloakConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakConverter))
            )
            .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new TenantMdcFilter(), TenantContextFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
