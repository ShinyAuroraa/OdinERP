package com.odin.wms.controller;

import com.odin.wms.domain.entity.PackingOrder.PackingStatus;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.PackingItemResponse;
import com.odin.wms.dto.response.PackingLabelResponse;
import com.odin.wms.dto.response.PackingOrderResponse;
import com.odin.wms.service.PackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints da estação de packing (embalagem).
 * Autorização por método conforme AC13.
 */
@RestController
@RequestMapping("/api/v1/packing-orders")
@RequiredArgsConstructor
public class PackingController {

    private final PackingService packingService;

    /**
     * AC5 — Abrir packing order (PENDING → IN_PROGRESS).
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public PackingOrderResponse openPacking(
            @PathVariable UUID id,
            @RequestBody OpenPackingRequest request) {
        return packingService.openPacking(id, request);
    }

    /**
     * AC6 — Escanear item via código de barras.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/scan-item/{itemId}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PackingItemResponse scanItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody ScanItemRequest request) {
        return packingService.scanItem(id, itemId, request);
    }

    /**
     * AC7 — Registrar peso, embalagem e dimensões.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/set-details")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PackingOrderResponse setPackageDetails(
            @PathVariable UUID id,
            @RequestBody SetPackageDetailsRequest request) {
        return packingService.setPackageDetails(id, request);
    }

    /**
     * AC8 — Gerar etiqueta SSCC + GS1-128.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/generate-label")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PackingLabelResponse generateLabel(@PathVariable UUID id) {
        return packingService.generateLabel(id);
    }

    /**
     * AC9 — Completar packing (fechar caixa e publicar evento).
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public PackingOrderResponse completePacking(@PathVariable UUID id) {
        return packingService.completePacking(id);
    }

    /**
     * AC10 — Cancelar packing order.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public PackingOrderResponse cancelPacking(
            @PathVariable UUID id,
            @RequestBody CancelPackingOrderRequest request) {
        return packingService.cancelPacking(id, request);
    }

    /**
     * AC11 — Buscar packing order por ID (inclui itens).
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PackingOrderResponse getOrder(@PathVariable UUID id) {
        return packingService.getOrder(id);
    }

    /**
     * AC11 — Listar packing orders com filtro opcional por status.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public Page<PackingOrderResponse> listOrders(
            @RequestParam(required = false) PackingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return packingService.listOrders(status, pageable);
    }
}
