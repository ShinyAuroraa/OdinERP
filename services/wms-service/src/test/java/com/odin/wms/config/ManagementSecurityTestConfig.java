package com.odin.wms.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration that permits all actuator endpoints.
 *
 * <p>Spring Boot's management server (separate port) runs in a child ApplicationContext.
 * The {@code spring.autoconfigure.exclude} mechanism does not reliably propagate to this
 * child context for {@code ManagementWebSecurityAutoConfiguration}, which blocks prometheus
 * while permitting health. This filter chain (order=1) overrides the management security
 * by matching all actuator endpoints and permitting them unconditionally.
 *
 * <p>Active only in the {@code test} profile — never applied to production.
 */
@Configuration
@Profile("test")
public class ManagementSecurityTestConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain managementTestSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
