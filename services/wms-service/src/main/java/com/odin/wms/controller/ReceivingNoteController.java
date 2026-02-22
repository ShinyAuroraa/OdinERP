package com.odin.wms.controller;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.enums.ReceivingStatus;
import com.odin.wms.dto.request.ConfirmReceivingItemRequest;
import com.odin.wms.dto.request.CreateReceivingNoteRequest;
import com.odin.wms.dto.response.PutawayTaskResponse;
import com.odin.wms.dto.response.ReceivingNoteResponse;
import com.odin.wms.service.PutawayService;
import com.odin.wms.service.ReceivingNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/receiving-notes")
@RequiredArgsConstructor
public class ReceivingNoteController {

    private final ReceivingNoteService receivingNoteService;
    private final PutawayService putawayService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ReceivingNoteResponse create(@Valid @RequestBody CreateReceivingNoteRequest request) {
        return receivingNoteService.create(request, TenantContextHolder.getTenantId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public List<ReceivingNoteResponse> findAll(
            @RequestParam(required = false) ReceivingStatus status,
            @RequestParam(required = false) UUID warehouseId) {
        return receivingNoteService.findAll(TenantContextHolder.getTenantId(), status, warehouseId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ReceivingNoteResponse findById(@PathVariable UUID id) {
        return receivingNoteService.findById(id, TenantContextHolder.getTenantId());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ReceivingNoteResponse start(@PathVariable UUID id) {
        return receivingNoteService.start(id, TenantContextHolder.getTenantId());
    }

    @PostMapping("/{id}/items/{itemId}/confirm")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ReceivingNoteResponse confirmItem(@PathVariable UUID id,
                                             @PathVariable UUID itemId,
                                             @Valid @RequestBody ConfirmReceivingItemRequest request) {
        return receivingNoteService.confirmItem(id, itemId, request, TenantContextHolder.getTenantId());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ReceivingNoteResponse complete(@PathVariable UUID id) {
        return receivingNoteService.complete(id, TenantContextHolder.getTenantId());
    }

    @PostMapping("/{id}/approve-divergences")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public ReceivingNoteResponse approveDivergences(@PathVariable UUID id) {
        return receivingNoteService.approveDivergences(id, TenantContextHolder.getTenantId());
    }

    @PostMapping("/{id}/putaway-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public List<PutawayTaskResponse> generatePutawayTasks(@PathVariable UUID id) {
        return putawayService.generateTasks(id, TenantContextHolder.getTenantId());
    }
}
