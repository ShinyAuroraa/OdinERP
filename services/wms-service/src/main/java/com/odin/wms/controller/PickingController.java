package com.odin.wms.controller;

import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import com.odin.wms.dto.request.*;
import com.odin.wms.dto.response.PickingItemResponse;
import com.odin.wms.dto.response.PickingOrderResponse;
import com.odin.wms.service.PickingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints de picking — separação de pedidos.
 * Autorização por método conforme AC12.
 */
@RestController
@RequestMapping("/api/v1/picking-orders")
@RequiredArgsConstructor
public class PickingController {

    private final PickingService pickingService;

    /**
     * AC4 — Criar picking order manualmente.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN (OPERATOR não pode criar manualmente).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public PickingOrderResponse createPickingOrder(
            @Valid @RequestBody CreatePickingOrderRequest request) {
        return pickingService.createPickingOrderManual(request);
    }

    /**
     * AC5 — Atribuir operador e reservar estoque.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PickingOrderResponse assignOrder(
            @PathVariable UUID id,
            @Valid @RequestBody AssignPickingOrderRequest request) {
        return pickingService.assignOrder(id, request);
    }

    /**
     * AC6 — Confirmar coleta de item via scanner.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/pick-item/{itemId}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PickingItemResponse pickItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody PickItemRequest request) {
        return pickingService.pickItem(id, itemId, request);
    }

    /**
     * AC7 — Substituir localização de picking.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/pick-item/{itemId}/substitute-location")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PickingItemResponse substituteLocation(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody SubstituteLocationRequest request) {
        return pickingService.substituteLocation(id, itemId, request);
    }

    /**
     * AC8 — Completar ordem de picking.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public PickingOrderResponse completeOrder(@PathVariable UUID id) {
        return pickingService.completeOrder(id);
    }

    /**
     * AC9 — Cancelar ordem de picking.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public PickingOrderResponse cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelPickingOrderRequest request) {
        return pickingService.cancelOrder(id, request);
    }

    /**
     * AC10 — Buscar picking order por ID.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public PickingOrderResponse getOrder(@PathVariable UUID id) {
        return pickingService.getOrder(id);
    }

    /**
     * AC10 — Listar picking orders paginadas com filtros opcionais.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public Page<PickingOrderResponse> listOrders(
            @RequestParam(required = false) PickingStatus status,
            @RequestParam(required = false) UUID operatorId,
            @PageableDefault(size = 20) Pageable pageable) {
        return pickingService.listOrders(status, operatorId, pageable);
    }
}
