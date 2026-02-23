package com.odin.wms.controller;

import com.odin.wms.dto.request.ReportScheduleRequest;
import com.odin.wms.dto.response.ReportScheduleResponse;
import com.odin.wms.service.ReportScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints de gerenciamento de agendamentos de relatórios.
 */
@RestController
@RequestMapping("/api/v1/reports/schedules")
@RequiredArgsConstructor
public class ReportScheduleController {

    private final ReportScheduleService reportScheduleService;

    /**
     * AC6 — Criar agendamento.
     * Role: WMS_ADMIN apenas.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ReportScheduleResponse createSchedule(
            @Valid @RequestBody ReportScheduleRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        return reportScheduleService.createSchedule(request, tenantId);
    }

    /**
     * AC6 — Listar agendamentos do tenant.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public Page<ReportScheduleResponse> listSchedules(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        return reportScheduleService.listSchedules(tenantId, pageable);
    }

    /**
     * AC6 — Remover agendamento.
     * Role: WMS_ADMIN apenas.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public void deleteSchedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        reportScheduleService.deleteSchedule(id, tenantId);
    }
}
