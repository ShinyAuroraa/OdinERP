package com.odin.wms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for observability endpoints.
 *
 * <p>Verifies that:
 * <ol>
 *   <li>The {@code /actuator/prometheus} endpoint is accessible on the management port
 *       without authentication.</li>
 *   <li>The response contains WMS custom metrics (prefixed with {@code wms_}).</li>
 * </ol>
 *
 * <p>Extends {@link AbstractIntegrationTest} for Testcontainers infrastructure (Postgres,
 * Redis, Elasticsearch) and the {@code ManagementWebSecurityAutoConfiguration} exclusion
 * which allows unauthenticated access to management endpoints.
 */
class ObservabilityIntegrationTest extends AbstractIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void prometheusEndpointIsAccessibleWithoutAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/prometheus",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheusEndpointContainsWmsMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/prometheus",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("wms_stock_movements");
    }
}
