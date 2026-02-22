package com.odin.wms.controller;

import com.odin.wms.dto.request.CreateAisleRequest;
import com.odin.wms.dto.request.UpdateAisleRequest;
import com.odin.wms.dto.response.AisleResponse;
import com.odin.wms.service.AisleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AisleController {

    private final AisleService aisleService;

    @PostMapping("/api/v1/zones/{zoneId}/aisles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public AisleResponse create(@PathVariable UUID zoneId,
                                @Valid @RequestBody CreateAisleRequest request) {
        return aisleService.create(zoneId, request);
    }

    @GetMapping("/api/v1/zones/{zoneId}/aisles")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public List<AisleResponse> findAllByZone(@PathVariable UUID zoneId) {
        return aisleService.findAllByZone(zoneId);
    }

    @GetMapping("/api/v1/aisles/{id}")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public AisleResponse findById(@PathVariable UUID id) {
        return aisleService.findById(id);
    }

    @PutMapping("/api/v1/aisles/{id}")
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public AisleResponse update(@PathVariable UUID id,
                                @Valid @RequestBody UpdateAisleRequest request) {
        return aisleService.update(id, request);
    }
}
