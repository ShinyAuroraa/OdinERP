package com.odin.wms.controller;

import com.odin.wms.domain.entity.ShippingOrder.ShippingStatus;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.ShippingItemResponse;
import com.odin.wms.dto.response.ShippingManifestResponse;
import com.odin.wms.dto.response.ShippingOrderResponse;
import com.odin.wms.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints da estação de expedição (shipping).
 * Autorização por método conforme AC4–AC11.
 */
@RestController
@RequestMapping("/api/v1/shipping-orders")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    /**
     * AC4 — Iniciar carregamento (PENDING → IN_PROGRESS).
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/start-loading")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ShippingOrderResponse startLoading(
            @PathVariable UUID id,
            @RequestBody StartLoadingRequest request) {
        return shippingService.startLoading(id, request);
    }

    /**
     * AC5 — Confirmar carregamento de item via scanner.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/load-item/{itemId}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public ShippingItemResponse loadItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody LoadItemRequest request) {
        return shippingService.loadItem(id, itemId, request);
    }

    /**
     * AC6 — Registrar dados da transportadora.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/set-carrier-details")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ShippingOrderResponse setCarrierDetails(
            @PathVariable UUID id,
            @RequestBody SetCarrierDetailsRequest request) {
        return shippingService.setCarrierDetails(id, request);
    }

    /**
     * AC7 — Gerar manifesto de carga (idempotente).
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/generate-manifest")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ShippingManifestResponse generateManifest(@PathVariable UUID id) {
        return shippingService.generateManifest(id);
    }

    /**
     * AC8 — Despachar envio (DISPATCHED).
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ShippingOrderResponse dispatch(@PathVariable UUID id) {
        return shippingService.dispatchShipping(id);
    }

    /**
     * AC9 — Cancelar shipping order.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public ShippingOrderResponse cancel(
            @PathVariable UUID id,
            @RequestBody CancelShippingOrderRequest request) {
        return shippingService.cancelShipping(id, request);
    }

    /**
     * AC10 — Buscar shipping order por ID (inclui itens).
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public ShippingOrderResponse getOrder(@PathVariable UUID id) {
        return shippingService.getOrder(id);
    }

    /**
     * AC11 — Listar shipping orders com filtro opcional por status.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public Page<ShippingOrderResponse> listOrders(
            @RequestParam(required = false) ShippingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return shippingService.listOrders(status, pageable);
    }
}
