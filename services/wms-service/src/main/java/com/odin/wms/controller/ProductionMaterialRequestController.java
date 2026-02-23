package com.odin.wms.controller;

import com.odin.wms.domain.entity.ProductionMaterialRequest.MaterialRequestStatus;
import com.odin.wms.dto.request.ReceiveFinishedGoodsRequest;
import com.odin.wms.dto.response.ProductionMaterialRequestResponse;
import com.odin.wms.service.ProductionIntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints para integração WMS↔MRP: requisições de material de produção.
 * AC8 — busca por ID; AC9 — listagem paginada;
 * AC5 — confirmar entrega; AC7 — receber produto acabado.
 */
@RestController
@RequestMapping("/api/v1/production-material-requests")
@RequiredArgsConstructor
public class ProductionMaterialRequestController {

    private final ProductionIntegrationService productionIntegrationService;

    /**
     * AC8 — Buscar requisição por ID.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public ProductionMaterialRequestResponse getRequest(@PathVariable UUID id) {
        return productionIntegrationService.getRequest(id);
    }

    /**
     * AC9 — Listar requisições paginadas, filtro opcional por status.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public Page<ProductionMaterialRequestResponse> listRequests(
            @RequestParam(required = false) MaterialRequestStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return productionIntegrationService.listRequests(status, pageable);
    }

    /**
     * AC5/AC6 — Confirmar entrega de matéria-prima na linha de produção.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PostMapping("/{id}/confirm-delivery")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public ProductionMaterialRequestResponse confirmDelivery(@PathVariable UUID id) {
        return productionIntegrationService.confirmDelivery(id);
    }

    /**
     * AC7 — Registrar recebimento de produto acabado no armazém.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PostMapping("/{id}/receive-finished-goods")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public ProductionMaterialRequestResponse receiveFinishedGoods(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveFinishedGoodsRequest request) {
        return productionIntegrationService.receiveFinishedGoods(id, request);
    }
}
