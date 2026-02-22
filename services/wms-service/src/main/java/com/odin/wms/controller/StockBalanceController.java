package com.odin.wms.controller;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.dto.response.StockBalanceResponse;
import com.odin.wms.dto.response.WarehouseOccupationResponse;
import com.odin.wms.service.StockBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de consulta de saldo de estoque em tempo real.
 * Todos os endpoints são read-only e acessíveis para qualquer role WMS autenticada.
 */
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockBalanceController {

    private final StockBalanceService stockBalanceService;

    /**
     * GET /api/v1/stock/balance
     * Lista saldo de estoque com filtros opcionais cumulativos (AND).
     * Lista vazia retorna 200 [] (não 404).
     */
    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public List<StockBalanceResponse> getBalance(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) UUID warehouseId) {

        return stockBalanceService.getBalance(
                TenantContextHolder.getTenantId(),
                productId, locationId, lotId, warehouseId);
    }

    /**
     * GET /api/v1/stock/balance/location/{locationId}
     * Lista saldo de estoque de uma localização específica.
     * Retorna 404 se a localização não existe ou pertence a outro tenant.
     */
    @GetMapping("/balance/location/{locationId}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public List<StockBalanceResponse> getLocationBalance(@PathVariable UUID locationId) {
        return stockBalanceService.getLocationBalance(
                TenantContextHolder.getTenantId(), locationId);
    }

    /**
     * GET /api/v1/stock/occupation?warehouseId={id}
     * Retorna taxa de ocupação do armazém detalhada por zona.
     * Retorna 404 se o warehouse não existe ou pertence a outro tenant.
     */
    @GetMapping("/occupation")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public WarehouseOccupationResponse getOccupation(
            @RequestParam UUID warehouseId) {
        return stockBalanceService.getOccupation(
                TenantContextHolder.getTenantId(), warehouseId);
    }
}
