package com.odin.wms.audit;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.entity.TenantRetentionConfig;
import com.odin.wms.domain.enums.AuditAction;
import com.odin.wms.domain.repository.AuditLogRepository;
import com.odin.wms.domain.repository.TenantRetentionConfigRepository;
import com.odin.wms.dto.request.UpdateRetentionConfigRequest;
import com.odin.wms.dto.response.AuditLogEntryResponse;
import com.odin.wms.dto.response.AuditLogExportResponse;
import com.odin.wms.dto.response.RetentionConfigResponse;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do AuditService — U3 a U8.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private TenantRetentionConfigRepository tenantRetentionConfigRepository;

    @InjectMocks
    private AuditService auditService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // U3 — getAuditLog_validFilters_returnsPaged
    // -------------------------------------------------------------------------

    @Test
    void getAuditLog_validFilters_returnsPaged() throws Exception {
        Instant from = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant to   = Instant.now();
        AuditLog entry = buildAuditLog(TENANT_ID);
        Page<AuditLog> page = new PageImpl<>(List.of(entry));

        when(auditLogRepository.findByTenantIdAndEntityTypeAndCreatedAtBetween(
                eq(TENANT_ID), eq("STOCK_ITEM"), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(page);

        Page<AuditLogEntryResponse> result = auditService.getAuditLog(
                "STOCK_ITEM", from, to, Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).entityType()).isEqualTo("STOCK_ITEM");
    }

    // -------------------------------------------------------------------------
    // U4 — getAuditLog_noFilters_returnsPaged
    // -------------------------------------------------------------------------

    @Test
    void getAuditLog_noFilters_returnsPaged() throws Exception {
        AuditLog entry = buildAuditLog(TENANT_ID);
        Page<AuditLog> page = new PageImpl<>(List.of(entry));

        when(auditLogRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(page);

        Page<AuditLogEntryResponse> result = auditService.getAuditLog(
                null, null, null, Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(auditLogRepository).findByTenantId(eq(TENANT_ID), any(Pageable.class));
        verify(auditLogRepository, never())
                .findByTenantIdAndCreatedAtBetween(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // U5 — getAuditLog_rangeBeyond90Days_throwsException
    // -------------------------------------------------------------------------

    @Test
    void getAuditLog_rangeBeyond90Days_throwsException() {
        Instant from = Instant.now().minus(91, ChronoUnit.DAYS);
        Instant to   = Instant.now();

        assertThatThrownBy(() -> auditService.getAuditLog(null, from, to, Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("90");
    }

    // -------------------------------------------------------------------------
    // U6 — exportAuditLog_rangeBeyond30Days_throwsException
    // -------------------------------------------------------------------------

    @Test
    void exportAuditLog_rangeBeyond30Days_throwsException() {
        Instant from = Instant.now().minus(31, ChronoUnit.DAYS);
        Instant to   = Instant.now();

        assertThatThrownBy(() -> auditService.exportAuditLog(from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30");
    }

    // -------------------------------------------------------------------------
    // U7 — getRetentionConfig_notConfigured_returnsDefault
    // -------------------------------------------------------------------------

    @Test
    void getRetentionConfig_notConfigured_returnsDefault() {
        when(tenantRetentionConfigRepository.findByTenantId(TENANT_ID))
                .thenReturn(Optional.empty());

        RetentionConfigResponse resp = auditService.getRetentionConfig();

        assertThat(resp.retentionMonths()).isEqualTo(84);
        assertThat(resp.retentionDescription()).contains("LGPD");
    }

    // -------------------------------------------------------------------------
    // U8 — updateRetentionConfig_below12Months_throwsException
    // -------------------------------------------------------------------------

    @Test
    void updateRetentionConfig_below12Months_throwsException() {
        assertThatThrownBy(() ->
                auditService.updateRetentionConfig(new UpdateRetentionConfigRequest(6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 meses");
    }

    // -------------------------------------------------------------------------
    // Extra — getAuditLogById_differentTenant_returns403
    // -------------------------------------------------------------------------

    @Test
    void getAuditLogById_differentTenant_returns403() throws Exception {
        UUID otherId = UUID.randomUUID();
        AuditLog entry = buildAuditLog(otherId); // pertence a outro tenant
        UUID logId = UUID.randomUUID();
        setId(entry, logId);

        when(auditLogRepository.findById(logId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> auditService.getAuditLogById(logId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private AuditLog buildAuditLog(UUID tenantId) throws Exception {
        AuditLog entry = AuditLog.builder()
                .tenantId(tenantId)
                .entityType("STOCK_ITEM")
                .entityId(UUID.randomUUID())
                .action(AuditAction.MOVEMENT)
                .actorId(UUID.randomUUID())
                .build();
        setId(entry, UUID.randomUUID());
        return entry;
    }

    private void setId(AuditLog entry, UUID id) throws Exception {
        Field field = Class.forName("com.odin.wms.domain.entity.base.BaseAppendOnlyEntity")
                .getDeclaredField("id");
        field.setAccessible(true);
        field.set(entry, id);
    }
}
