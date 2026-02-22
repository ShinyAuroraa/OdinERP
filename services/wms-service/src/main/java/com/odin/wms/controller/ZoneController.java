package com.odin.wms.controller;

import com.odin.wms.dto.request.CreateZoneRequest;
import com.odin.wms.dto.request.UpdateZoneRequest;
import com.odin.wms.dto.response.ZoneResponse;
import com.odin.wms.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @PostMapping("/api/v1/warehouses/{warehouseId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ZoneResponse create(@PathVariable UUID warehouseId,
                               @Valid @RequestBody CreateZoneRequest request) {
        return zoneService.create(warehouseId, request);
    }

    @GetMapping("/api/v1/warehouses/{warehouseId}/zones")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public List<ZoneResponse> findAllByWarehouse(@PathVariable UUID warehouseId) {
        return zoneService.findAllByWarehouse(warehouseId);
    }

    @GetMapping("/api/v1/zones/{id}")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public ZoneResponse findById(@PathVariable UUID id) {
        return zoneService.findById(id);
    }

    @PutMapping("/api/v1/zones/{id}")
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ZoneResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateZoneRequest request) {
        return zoneService.update(id, request);
    }

    @PatchMapping("/api/v1/zones/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public void deactivate(@PathVariable UUID id) {
        zoneService.deactivate(id);
    }
}
