package com.odin.wms.audit;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.repository.AuditLogRepository;
import com.odin.wms.infrastructure.elasticsearch.AuditLogEsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa imutabilidade do audit_log via trigger PostgreSQL — I9 (AC10).
 * Trigger trg_audit_log_immutable bloqueia UPDATE e DELETE em audit_log.
 */
class AuditImmutabilityTest extends AbstractIntegrationTest {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean private AuditLogEsRepository auditLogEsRepository;

    // -------------------------------------------------------------------------
    // I9 — auditLogImmutability_updateAttempt_throwsException (AC10)
    // -------------------------------------------------------------------------

    @Test
    void auditLogImmutability_updateAttempt_throwsDataIntegrityViolation() {
        AuditLog entry = auditLogRepository.save(AuditLog.builder()
                .tenantId(UUID.randomUUID())
                .entityType("STOCK_ITEM")
                .entityId(UUID.randomUUID())
                .action(AuditAction.MOVEMENT)
                .actorId(UUID.randomUUID())
                .build());

        UUID id = entry.getId();

        // O trigger trg_audit_log_immutable deve rejeitar UPDATE com ERRCODE = 'restrict_violation'
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE audit_log SET action = 'DELETE' WHERE id = ?::uuid",
                        id.toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
