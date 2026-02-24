package br.com.odin.crm.web;

import br.com.odin.crm.infrastructure.persistence.audit.AuditLogJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies the full Spring context loads without errors.
 *
 * <p>Infrastructure autoconfiguration (DataSource, JPA, Kafka, Redis, OAuth2)
 * is excluded via spring.autoconfigure.exclude so this test runs without
 * live Docker services. Testcontainers integration is added in Story 1.2.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
        }
)
@ActiveProfiles("test")
class CrmApplicationSmokeTest {

    // SecurityConfig requires a JwtDecoder; mock it since OAuth2 auto-config is excluded in this smoke test
    @MockBean
    private JwtDecoder jwtDecoder;

    // JPA auto-config is excluded; UserAdminService requires AuditLogJpaRepository — mock it
    @MockBean
    private AuditLogJpaRepository auditLogRepository;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void springApplicationBeanIsRegistered() {
        assertThat(context.containsBean("crmApplication")).isTrue();
    }
}
