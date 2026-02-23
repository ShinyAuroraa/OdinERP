package com.odin.wms.controller;

import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Endpoints de geração de relatórios regulatórios.
 * Todos os endpoints requerem autenticação JWT com tenant_id claim.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * AC1 — Ficha de Estoque (Receita Federal).
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/ficha-estoque")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ResponseEntity<?> fichaEstoque(
            @RequestParam UUID warehouseId,
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "JSON") ExportFormat format,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        return reportService.generateFichaEstoque(warehouseId, dataInicio, dataFim, productId, format, tenantId);
    }

    /**
     * AC2 — Controle ANVISA Vigilância Sanitária.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/anvisa")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ResponseEntity<?> anvisa(
            @RequestParam UUID warehouseId,
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(defaultValue = "JSON") ExportFormat format,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        return reportService.generateAnvisa(warehouseId, dataInicio, dataFim, productId, lotId, format, tenantId);
    }

    /**
     * AC3 — Rastreabilidade por Lote para Recall.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/rastreabilidade-lote/{lotId}")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ResponseEntity<?> rastreabilidadeLote(
            @PathVariable UUID lotId,
            @RequestParam(defaultValue = "JSON") ExportFormat format,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        return reportService.generateRastreabilidadeLote(lotId, format, tenantId);
    }

    /**
     * AC4 — Relatório de Movimentações por Período.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/movimentacoes")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public ResponseEntity<?> movimentacoes(
            @RequestParam UUID warehouseId,
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(defaultValue = "JSON") ExportFormat format,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        return reportService.generateMovimentacoes(
                warehouseId, dataInicio, dataFim, productId, movementType, locationId, format, tenantId, pageable);
    }
}
