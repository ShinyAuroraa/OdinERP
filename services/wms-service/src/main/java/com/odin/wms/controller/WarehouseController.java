package com.odin.wms.controller;

import com.odin.wms.dto.request.CreateWarehouseRequest;
import com.odin.wms.dto.request.UpdateWarehouseRequest;
import com.odin.wms.dto.response.WarehouseResponse;
import com.odin.wms.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public WarehouseResponse create(@Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public List<WarehouseResponse> findAll() {
        return warehouseService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public WarehouseResponse findById(@PathVariable UUID id) {
        return warehouseService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public WarehouseResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateWarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public void deactivate(@PathVariable UUID id) {
        warehouseService.deactivate(id);
    }
}
