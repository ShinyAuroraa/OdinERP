package com.odin.wms.controller;

import com.odin.wms.domain.entity.InternalTransfer.TransferStatus;
import com.odin.wms.dto.request.CancelTransferRequest;
import com.odin.wms.dto.request.ConfirmTransferRequest;
import com.odin.wms.dto.request.CreateTransferRequest;
import com.odin.wms.dto.response.InternalTransferResponse;
import com.odin.wms.service.InternalTransferService;
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
 * Endpoints de transferências internas entre posições de armazém.
 * Autorização por método conforme AC11.
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class InternalTransferController {

    private final InternalTransferService internalTransferService;

    /**
     * AC3 — Criar transferência manual.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public InternalTransferResponse createTransfer(
            @Valid @RequestBody CreateTransferRequest request) {
        return internalTransferService.createTransfer(request);
    }

    /**
     * AC4 — Confirmar transferência via scanner.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public InternalTransferResponse confirmTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmTransferRequest request) {
        return internalTransferService.confirmTransfer(id, request);
    }

    /**
     * AC8 — Cancelar transferência.
     * Roles: WMS_SUPERVISOR, WMS_ADMIN apenas — operadores NÃO podem cancelar.
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR','WMS_ADMIN')")
    public InternalTransferResponse cancelTransfer(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelTransferRequest request) {
        return internalTransferService.cancelTransfer(id, request);
    }

    /**
     * AC9 — Buscar transferência por ID.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public InternalTransferResponse getTransfer(@PathVariable UUID id) {
        return internalTransferService.getTransfer(id);
    }

    /**
     * AC9 — Listar transferências paginadas, filtro opcional por status.
     * Roles: WMS_OPERATOR, WMS_SUPERVISOR, WMS_ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR','WMS_SUPERVISOR','WMS_ADMIN')")
    public Page<InternalTransferResponse> listTransfers(
            @RequestParam(required = false) TransferStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return internalTransferService.listTransfers(status, pageable);
    }
}
