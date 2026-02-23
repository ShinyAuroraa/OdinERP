package com.odin.wms.controller;

import com.odin.wms.dto.request.CreateInventoryCountRequest;
import com.odin.wms.dto.request.SecondCountRequest;
import com.odin.wms.dto.request.SubmitCountRequest;
import com.odin.wms.dto.response.*;
import com.odin.wms.service.InventoryService;
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
 * Endpoints de inventário físico (contagem de estoque).
 * Ciclo: criar → iniciar → contar → reconciliar → aprovar → fechar.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /** AC1 — Criar sessão de inventário (DRAFT). Supervisor/Admin only. */
    @PostMapping("/count")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public InventoryCountResponse createCount(@Valid @RequestBody CreateInventoryCountRequest request) {
        return inventoryService.createCount(request);
    }

    /** AC2 — Iniciar contagem (DRAFT → IN_PROGRESS). Supervisor/Admin only. */
    @PostMapping("/count/{countId}/start")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public InventoryCountResponse startCount(@PathVariable UUID countId) {
        return inventoryService.startCount(countId);
    }

    /** AC3 — Lista paginada de itens de contagem. Operator pode consultar. */
    @GetMapping("/count/{countId}/items")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public Page<InventoryCountItemResponse> getCountItems(
            @PathVariable UUID countId,
            @PageableDefault(size = 50) Pageable pageable) {
        return inventoryService.getCountItems(countId, pageable);
    }

    /** AC4 — Submeter contagem de um item. Operator pode contar. */
    @PatchMapping("/count/{countId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public InventoryCountItemResponse submitCount(
            @PathVariable UUID countId,
            @PathVariable UUID itemId,
            @Valid @RequestBody SubmitCountRequest request) {
        return inventoryService.submitCount(countId, itemId, request);
    }

    /** AC5 — Reconciliar divergências. Supervisor/Admin only. */
    @PostMapping("/count/{countId}/reconcile")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ReconciliationSummaryResponse reconcile(@PathVariable UUID countId) {
        return inventoryService.reconcile(countId);
    }

    /** AC6 — Segunda contagem para itens com divergência. Supervisor/Admin only. */
    @PostMapping("/count/{countId}/items/{itemId}/second-count")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public InventoryCountItemResponse submitSecondCount(
            @PathVariable UUID countId,
            @PathVariable UUID itemId,
            @Valid @RequestBody SecondCountRequest request) {
        return inventoryService.submitSecondCount(countId, itemId, request);
    }

    /** AC7 — Aprovar ajustes e atualizar StockItems. Supervisor/Admin only. */
    @PostMapping("/count/{countId}/approve")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ApprovalSummaryResponse approveAdjustments(@PathVariable UUID countId) {
        return inventoryService.approveAdjustments(countId);
    }

    /** AC8 — Fechar inventário (APPROVED → CLOSED). Supervisor/Admin only. */
    @PostMapping("/count/{countId}/close")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public InventoryCountResponse closeCount(@PathVariable UUID countId) {
        return inventoryService.closeCount(countId);
    }
}
