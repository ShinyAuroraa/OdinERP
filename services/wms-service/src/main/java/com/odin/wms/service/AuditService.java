package com.odin.wms.service;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.entity.TenantRetentionConfig;
import com.odin.wms.domain.repository.AuditLogRepository;
import com.odin.wms.domain.repository.TenantRetentionConfigRepository;
import com.odin.wms.dto.request.UpdateRetentionConfigRequest;
import com.odin.wms.dto.response.AuditLogEntryResponse;
import com.odin.wms.dto.response.AuditLogExportResponse;
import com.odin.wms.dto.response.RetentionConfigResponse;
import com.odin.wms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service de auditoria — listagem, export e configuração de retenção LGPD.
 * Todos os métodos exigem tenantId do contexto (TenantContextHolder).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_LIST_DAYS   = 90;
    private static final int MAX_EXPORT_DAYS = 30;
    private static final int DEFAULT_RETENTION_MONTHS = 84;

    private final AuditLogRepository auditLogRepository;
    private final TenantRetentionConfigRepository tenantRetentionConfigRepository;

    // -------------------------------------------------------------------------
    // AC6 — GET /audit/log (listagem paginada)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AuditLogEntryResponse> getAuditLog(String entityType, Instant from, Instant to, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();

        validateDateRange(from, to, MAX_LIST_DAYS);

        Page<AuditLog> page;

        if (from != null && to != null && entityType != null) {
            page = auditLogRepository.findByTenantIdAndEntityTypeAndCreatedAtBetween(
                    tenantId, entityType, from, to, pageable);
        } else if (from != null && to != null) {
            page = auditLogRepository.findByTenantIdAndCreatedAtBetween(
                    tenantId, from, to, pageable);
        } else {
            page = auditLogRepository.findByTenantId(tenantId, pageable);
        }

        return page.map(AuditLogEntryResponse::from);
    }

    // -------------------------------------------------------------------------
    // AC7 — GET /audit/log/{id}
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AuditLogExportResponse getAuditLogById(UUID id) {
        UUID tenantId = TenantContextHolder.getTenantId();

        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog não encontrado: " + id));

        // Tenant isolation — retorna 403 para não vazar existência de outro tenant
        if (!log.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acesso negado ao registro de auditoria: " + id);
        }

        return AuditLogExportResponse.from(log);
    }

    // -------------------------------------------------------------------------
    // AC8 — GET /audit/log/export
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AuditLogExportResponse> exportAuditLog(Instant from, Instant to) {
        UUID tenantId = TenantContextHolder.getTenantId();

        if (from == null || to == null) {
            throw new IllegalArgumentException("Parâmetros 'from' e 'to' são obrigatórios para export.");
        }

        validateDateRange(from, to, MAX_EXPORT_DAYS);

        // Máximo de 10.000 registros por export — usa Pageable com limit
        Page<AuditLog> page = auditLogRepository.findByTenantIdAndCreatedAtBetween(
                tenantId, from, to, org.springframework.data.domain.PageRequest.of(0, 10_000));

        log.debug("Export auditoria: {} registros (tenant={}, from={}, to={})",
                page.getTotalElements(), tenantId, from, to);

        return page.getContent().stream()
                .map(AuditLogExportResponse::from)
                .toList();
    }

    // -------------------------------------------------------------------------
    // AC9 — GET /audit/retention-config
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public RetentionConfigResponse getRetentionConfig() {
        UUID tenantId = TenantContextHolder.getTenantId();

        return tenantRetentionConfigRepository.findByTenantId(tenantId)
                .map(c -> RetentionConfigResponse.of(tenantId, c.getRetentionMonths()))
                .orElse(RetentionConfigResponse.of(tenantId, DEFAULT_RETENTION_MONTHS));
    }

    // -------------------------------------------------------------------------
    // AC9 — PUT /audit/retention-config
    // -------------------------------------------------------------------------

    @Transactional
    public RetentionConfigResponse updateRetentionConfig(UpdateRetentionConfigRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        if (request.retentionMonths() < 12) {
            throw new IllegalArgumentException("Retenção mínima: 12 meses (compliance LGPD)");
        }

        TenantRetentionConfig config = tenantRetentionConfigRepository
                .findByTenantId(tenantId)
                .orElse(TenantRetentionConfig.builder()
                        .tenantId(tenantId)
                        .retentionMonths(DEFAULT_RETENTION_MONTHS)
                        .build());

        config.setRetentionMonths(request.retentionMonths());
        tenantRetentionConfigRepository.save(config);

        log.info("Retenção LGPD atualizada: {} meses (tenant={})", request.retentionMonths(), tenantId);

        return RetentionConfigResponse.of(tenantId, config.getRetentionMonths());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateDateRange(Instant from, Instant to, int maxDays) {
        if (from == null || to == null) {
            return; // sem filtro de data — válido para listagem
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > maxDays) {
            throw new IllegalArgumentException(
                    String.format("Range de datas excede o máximo permitido: %d dias (máximo: %d dias).", days, maxDays));
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' deve ser posterior a 'from'.");
        }
    }
}
