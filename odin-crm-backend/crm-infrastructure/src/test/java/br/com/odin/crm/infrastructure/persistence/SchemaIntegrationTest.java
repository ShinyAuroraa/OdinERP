package br.com.odin.crm.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that the Flyway migration V1__create_schema.sql ran correctly.
 *
 * <p>Verifies: pgcrypto extension, pg_trgm extension, set_updated_at() function,
 * and Flyway schema history.
 */
@DisplayName("V1 Schema Integration Test")
class SchemaIntegrationTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("pgcrypto extension must be installed")
    void pgcryptoExtensionIsInstalled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pgcrypto'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("pg_trgm extension must be installed")
    void pgTrgmExtensionIsInstalled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("set_updated_at() trigger function must exist")
    void setUpdatedAtFunctionExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_proc WHERE proname = 'set_updated_at'",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Flyway must have applied exactly 1 migration (V1)")
    void flywayAppliedV1Migration() {
        String version = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                String.class);
        assertThat(version).isEqualTo("1");
    }
}
