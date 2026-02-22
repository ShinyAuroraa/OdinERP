package com.odin.wms.controller;

import com.odin.wms.config.security.TenantContextHolder;
import com.odin.wms.domain.enums.QuarantineStatus;
import com.odin.wms.dto.request.DecideQuarantineRequest;
import com.odin.wms.dto.response.QuarantineTaskResponse;
import com.odin.wms.dto.response.QuarantineTaskSummaryResponse;
import com.odin.wms.service.QuarantineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quarantine-tasks")
@RequiredArgsConstructor
public class QuarantineController {

    private final QuarantineService quarantineService;

    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public List<QuarantineTaskSummaryResponse> findAll(
            @RequestParam(required = false) QuarantineStatus status,
            @RequestParam(required = false) UUID warehouseId) {
        return quarantineService.findAll(TenantContextHolder.getTenantId(), status, warehouseId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public QuarantineTaskResponse findById(@PathVariable UUID id) {
        return quarantineService.findById(id, TenantContextHolder.getTenantId());
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public QuarantineTaskResponse start(@PathVariable UUID id) {
        return quarantineService.start(id, TenantContextHolder.getTenantId());
    }

    @PatchMapping("/{id}/decide")
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public QuarantineTaskResponse decide(@PathVariable UUID id,
                                         @Valid @RequestBody DecideQuarantineRequest request) {
        return quarantineService.decide(id, request, TenantContextHolder.getTenantId());
    }

    @PatchMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('WMS_SUPERVISOR', 'WMS_ADMIN')")
    public void cancel(@PathVariable UUID id) {
        quarantineService.cancel(id, TenantContextHolder.getTenantId());
    }
}
