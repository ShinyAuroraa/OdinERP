package com.odin.wms.audit;

import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.repository.AuditLogRepository;
import com.odin.wms.infrastructure.elasticsearch.AuditLogEsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I10 — Fix REQ-001: exportAuditLog filename deve incluir tenantId.
 * Verifica que Content-Disposition contém "audit-log-{tenantId}-{date}.json".
 */
@AutoConfigureMockMvc
class AuditControllerREQ001Test extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditLogRepository auditLogRepository;

    @MockBean private AuditLogEsRepository auditLogEsRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .entityType("INTERNAL_TRANSFER")
                .entityId(UUID.randomUUID())
                .action(AuditAction.MOVEMENT)
                .actorId(UUID.randomUUID())
                .newValue("{\"movementType\":\"TRANSFER\",\"quantity\":5}")
                .build());
    }

    // -------------------------------------------------------------------------
    // I10 — exportAuditLog_filenameContainsTenantId (REQ-001 fix)
    // -------------------------------------------------------------------------

    @Test
    void exportAuditLog_filenameContainsTenantId() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to   = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/audit/log/export")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(jwt()
                                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                                           .claim("sub", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("audit-log-" + tenantId.toString() + "-")));
    }
}
