package br.com.odin.crm.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application anchor for crm-infrastructure integration tests.
 *
 * <p>{@code @DataJpaTest} requires a {@code @SpringBootConfiguration} in the classpath.
 * Since {@code CrmApplication} lives in the crm-web module (not a test dependency of
 * crm-infrastructure), this class provides the configuration anchor for test slices.
 */
@SpringBootApplication
class TestApplication {
}
