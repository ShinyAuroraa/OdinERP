package com.odin.wms.controller;

import com.odin.wms.dto.request.CreateShelfRequest;
import com.odin.wms.dto.request.UpdateShelfRequest;
import com.odin.wms.dto.response.ShelfResponse;
import com.odin.wms.service.ShelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShelfController {

    private final ShelfService shelfService;

    @PostMapping("/api/v1/aisles/{aisleId}/shelves")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ShelfResponse create(@PathVariable UUID aisleId,
                                @Valid @RequestBody CreateShelfRequest request) {
        return shelfService.create(aisleId, request);
    }

    @GetMapping("/api/v1/aisles/{aisleId}/shelves")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public List<ShelfResponse> findAllByAisle(@PathVariable UUID aisleId) {
        return shelfService.findAllByAisle(aisleId);
    }

    @GetMapping("/api/v1/shelves/{id}")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public ShelfResponse findById(@PathVariable UUID id) {
        return shelfService.findById(id);
    }

    @PutMapping("/api/v1/shelves/{id}")
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ShelfResponse update(@PathVariable UUID id,
                                @Valid @RequestBody UpdateShelfRequest request) {
        return shelfService.update(id, request);
    }
}
