package com.odin.wms.controller;

import com.odin.wms.dto.request.UpdateRetentionConfigRequest;
import com.odin.wms.dto.response.AuditLogEntryResponse;
import com.odin.wms.dto.response.AuditLogExportResponse;
import com.odin.wms.dto.response.RetentionConfigResponse;
import com.odin.wms.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints de auditoria regulatória.
 * Todos os endpoints exigem role WMS_ADMIN (AC11).
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WMS_ADMIN')")
public class AuditController {

    private final AuditService auditService;

    /**
     * AC6 — Listagem paginada do audit_log.
     * Filtros opcionais: entityType, action (futuro), from+to (máx 90 dias).
     */
    @GetMapping("/log")
    public Page<AuditLogEntryResponse> getAuditLog(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return auditService.getAuditLog(entityType, from, to, pageable);
    }

    /**
     * AC7 — Detalhes completos de um registro de auditoria (inclui oldValue/newValue).
     * UUID de outro tenant → 403 (não vaza existência).
     */
    @GetMapping("/log/{id}")
    public AuditLogExportResponse getAuditLogById(@PathVariable UUID id) {
        return auditService.getAuditLogById(id);
    }

    /**
     * AC8 — Export regulatório como JSON attachment.
     * Range from+to obrigatório; máx 30 dias; máx 10.000 registros.
     */
    @GetMapping("/log/export")
    public ResponseEntity<List<AuditLogExportResponse>> exportAuditLog(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        List<AuditLogExportResponse> data = auditService.exportAuditLog(from, to);

        String date = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        UUID tenantId = com.odin.wms.config.security.TenantContextHolder.getTenantId();
        String filename = String.format("audit-log-%s-%s.json", tenantId, date);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    /**
     * AC9 — Consulta configuração de retenção LGPD do tenant.
     * Retorna default 84 meses se não configurado.
     */
    @GetMapping("/retention-config")
    public RetentionConfigResponse getRetentionConfig() {
        return auditService.getRetentionConfig();
    }

    /**
     * AC9 — Atualiza configuração de retenção LGPD.
     * Mínimo 12 meses; default LGPD é 84 meses (7 anos).
     */
    @PutMapping("/retention-config")
    public RetentionConfigResponse updateRetentionConfig(
            @Valid @RequestBody UpdateRetentionConfigRequest request) {
        return auditService.updateRetentionConfig(request);
    }
}
