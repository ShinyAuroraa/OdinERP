package com.odin.wms.controller;

import com.odin.wms.domain.enums.LocationType;
import com.odin.wms.dto.request.CreateLocationRequest;
import com.odin.wms.dto.request.UpdateLocationRequest;
import com.odin.wms.dto.response.LocationResponse;
import com.odin.wms.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/api/v1/shelves/{shelfId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public LocationResponse create(@PathVariable UUID shelfId,
                                   @Valid @RequestBody CreateLocationRequest request) {
        return locationService.create(shelfId, request);
    }

    @GetMapping("/api/v1/shelves/{shelfId}/locations")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public List<LocationResponse> findByShelf(@PathVariable UUID shelfId) {
        return locationService.findAllByShelf(shelfId);
    }

    @GetMapping("/api/v1/locations/{id}")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public LocationResponse findById(@PathVariable UUID id) {
        return locationService.findById(id);
    }

    @GetMapping("/api/v1/locations")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public List<LocationResponse> findByType(@RequestParam LocationType type) {
        return locationService.findByType(type);
    }

    @PutMapping("/api/v1/locations/{id}")
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public LocationResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateLocationRequest request) {
        return locationService.update(id, request);
    }

    @PatchMapping("/api/v1/locations/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public void deactivate(@PathVariable UUID id) {
        locationService.deactivate(id);
    }
}
