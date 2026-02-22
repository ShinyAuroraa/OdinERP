package com.odin.wms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WMS Service Bootstrap — Integration Tests")
class WmsServiceBootstrapTest extends AbstractIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Spring context loads successfully")
    void contextLoads() {
        // If context fails to load, this test will fail with an exception
    }

    @Test
    @DisplayName("Actuator health endpoint returns UP")
    void healthEndpointReturnsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health",
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("Liveness probe returns UP")
    void livenessProbeReturnsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/liveness",
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("Readiness probe returns UP when all services connected")
    void readinessProbeReturnsUpWhenAllServicesConnected() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health/readiness",
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("Prometheus metrics endpoint is accessible")
    void prometheusEndpointIsAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/prometheus",
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }
}
