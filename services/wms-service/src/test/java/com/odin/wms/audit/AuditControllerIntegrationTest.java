package com.odin.wms.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.AbstractIntegrationTest;
import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.entity.TenantRetentionConfig;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.repository.AuditLogRepository;
import com.odin.wms.domain.repository.TenantRetentionConfigRepository;
import com.odin.wms.infrastructure.elasticsearch.AuditLogEsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para AuditController — I1 a I8 (AC6–AC11).
 */
@AutoConfigureMockMvc
class AuditControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private TenantRetentionConfigRepository tenantRetentionConfigRepository;

    @MockBean private AuditLogEsRepository auditLogEsRepository;

    private UUID tenantId;
    private UUID otherTenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // I1 — getAuditLog_asAdmin_returns200WithContent
    // -------------------------------------------------------------------------

    @Test
    void getAuditLog_asAdmin_returns200WithContent() throws Exception {
        saveAuditLog(tenantId, "STOCK_ITEM");

        mockMvc.perform(withAdmin(get("/api/v1/audit/log")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].entityType").value("STOCK_ITEM"));
    }

    // -------------------------------------------------------------------------
    // I2 — getAuditLog_asOperator_returns403
    // -------------------------------------------------------------------------

    @Test
    void getAuditLog_asOperator_returns403() throws Exception {
        mockMvc.perform(withOperator(get("/api/v1/audit/log")))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I3 — getAuditLogById_sameTenant_returns200
    // -------------------------------------------------------------------------

    @Test
    void getAuditLogById_sameTenant_returns200() throws Exception {
        AuditLog entry = saveAuditLog(tenantId, "STOCK_ITEM");

        mockMvc.perform(withAdmin(get("/api/v1/audit/log/{id}", entry.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entry.getId().toString()))
                .andExpect(jsonPath("$.entityType").value("STOCK_ITEM"));
    }

    // -------------------------------------------------------------------------
    // I4 — getAuditLogById_differentTenant_returns403
    // -------------------------------------------------------------------------

    @Test
    void getAuditLogById_differentTenant_returns403() throws Exception {
        // Salva log pertencendo a otherTenantId
        AuditLog entry = saveAuditLog(otherTenantId, "STOCK_ITEM");

        // Acessa como admin de tenantId (diferente)
        mockMvc.perform(withAdmin(get("/api/v1/audit/log/{id}", entry.getId())))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // I5 — exportAuditLog_validRange_returns200WithJson
    // -------------------------------------------------------------------------

    @Test
    void exportAuditLog_validRange_returns200WithJson() throws Exception {
        saveAuditLog(tenantId, "STOCK_ITEM");

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to   = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(withAdmin(get("/api/v1/audit/log/export")
                        .param("from", from.toString())
                        .param("to", to.toString())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("audit-log-")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    // -------------------------------------------------------------------------
    // I6 — exportAuditLog_rangeBeyond30Days_returns400
    // -------------------------------------------------------------------------

    @Test
    void exportAuditLog_rangeBeyond30Days_returns400() throws Exception {
        Instant from = Instant.now().minus(31, ChronoUnit.DAYS);
        Instant to   = Instant.now();

        mockMvc.perform(withAdmin(get("/api/v1/audit/log/export")
                        .param("from", from.toString())
                        .param("to", to.toString())))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // I7 — updateRetentionConfig_asAdmin_returns200
    // -------------------------------------------------------------------------

    @Test
    void updateRetentionConfig_asAdmin_returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("retentionMonths", 120));

        mockMvc.perform(withAdmin(put("/api/v1/audit/retention-config"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionMonths").value(120))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }

    // -------------------------------------------------------------------------
    // I8 — getRetentionConfig_notConfigured_returnsDefault84
    // -------------------------------------------------------------------------

    @Test
    void getRetentionConfig_notConfigured_returnsDefault84() throws Exception {
        // tenantId novo sem configuração prévia
        mockMvc.perform(withAdmin(get("/api/v1/audit/retention-config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionMonths").value(84))
                .andExpect(jsonPath("$.retentionDescription").value(containsString("LGPD")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MockHttpServletRequestBuilder withAdmin(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_ADMIN")));
    }

    private MockHttpServletRequestBuilder withOperator(MockHttpServletRequestBuilder req) {
        return req.with(jwt()
                .jwt(j -> j.claim("tenant_id", tenantId.toString())
                           .claim("sub", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_WMS_OPERATOR")));
    }

    private AuditLog saveAuditLog(UUID tenant, String entityType) {
        return auditLogRepository.save(AuditLog.builder()
                .tenantId(tenant)
                .entityType(entityType)
                .entityId(UUID.randomUUID())
                .action(AuditAction.MOVEMENT)
                .actorId(UUID.randomUUID())
                .build());
    }
}
