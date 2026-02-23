package com.odin.wms.controller;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.dto.response.ExpiryResponse;
import com.odin.wms.dto.response.LotTraceabilityResponse;
import com.odin.wms.dto.response.SerialTraceabilityResponse;
import com.odin.wms.dto.response.TraceabilityTreeResponse;
import com.odin.wms.service.LotTraceabilityService;
import com.odin.wms.service.SerialTraceabilityService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints de rastreabilidade de lotes, séries e validades.
 * Todos os endpoints são read-only (GET) e exigem role WMS.
 * AC1, AC2, AC3, AC4, AC8, AC9.
 */
@RestController
@RequestMapping("/api/v1/traceability")
@RequiredArgsConstructor
@Validated
public class TraceabilityController {

    private final LotTraceabilityService lotTraceabilityService;
    private final SerialTraceabilityService serialTraceabilityService;

    /**
     * GET /api/v1/traceability/lot/{lotNumber}
     * Histórico de movimentações de um lote.
     * AC1 — retorna 404 se lote não pertence ao tenant.
     */
    @GetMapping("/lot/{lotNumber}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public LotTraceabilityResponse getLotHistory(
            @PathVariable
            @Size(max = 50, message = "lotNumber deve ter no máximo 50 caracteres")
            @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "lotNumber contém caracteres inválidos")
            String lotNumber) {

        return lotTraceabilityService.getLotHistory(
                TenantContextHolder.getTenantId(), lotNumber);
    }

    /**
     * GET /api/v1/traceability/serial/{serialNumber}
     * Histórico de movimentações de um número de série.
     * AC2 — retorna 404 se série não pertence ao tenant.
     */
    @GetMapping("/serial/{serialNumber}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public SerialTraceabilityResponse getSerialHistory(
            @PathVariable
            @Size(max = 50, message = "serialNumber deve ter no máximo 50 caracteres")
            @Pattern(regexp = "^[A-Za-z0-9\\-_]+$", message = "serialNumber contém caracteres inválidos")
            String serialNumber) {

        return serialTraceabilityService.getSerialHistory(
                TenantContextHolder.getTenantId(), serialNumber);
    }

    /**
     * GET /api/v1/traceability/lot/{lotId}/tree
     * Árvore completa de rastreabilidade de um lote.
     * AC3 — limitada a wms.traceability.max-movements.
     */
    @GetMapping("/lot/{lotId}/tree")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public TraceabilityTreeResponse getTraceabilityTree(@PathVariable UUID lotId) {
        return lotTraceabilityService.getTraceabilityTree(
                TenantContextHolder.getTenantId(), lotId);
    }

    /**
     * GET /api/v1/traceability/product/{productId}/expiry
     * Lotes por validade (FEFO) com filtros opcionais.
     * AC4 — ordenado por expiryDate ASC.
     */
    @GetMapping("/product/{productId}/expiry")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public List<ExpiryResponse> getExpiryByProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryBefore) {

        return lotTraceabilityService.getExpiryByProduct(
                TenantContextHolder.getTenantId(), productId, warehouseId, expiryBefore);
    }
}
