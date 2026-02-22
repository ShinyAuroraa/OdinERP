package com.odin.wms.controller;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.enums.PutawayStatus;
import com.odin.wms.dto.request.ConfirmPutawayRequest;
import com.odin.wms.dto.response.PutawayTaskResponse;
import com.odin.wms.dto.response.PutawayTaskSummaryResponse;
import com.odin.wms.service.PutawayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/putaway-tasks")
@RequiredArgsConstructor
public class PutawayController {

    private final PutawayService putawayService;

    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public List<PutawayTaskSummaryResponse> findAll(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) PutawayStatus status) {
        return putawayService.findAll(TenantContextHolder.getTenantId(), warehouseId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public PutawayTaskResponse findById(@PathVariable UUID id) {
        return putawayService.findById(id, TenantContextHolder.getTenantId());
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public PutawayTaskResponse start(@PathVariable UUID id) {
        return putawayService.start(id, TenantContextHolder.getTenantId());
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public PutawayTaskResponse confirm(@PathVariable UUID id,
                                       @RequestBody(required = false) ConfirmPutawayRequest request) {
        return putawayService.confirm(id, request, TenantContextHolder.getTenantId());
    }

    @PatchMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public void cancel(@PathVariable UUID id) {
        putawayService.cancel(id, TenantContextHolder.getTenantId());
    }
}
