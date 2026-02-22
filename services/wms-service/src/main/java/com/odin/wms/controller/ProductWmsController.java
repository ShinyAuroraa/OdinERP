package com.odin.wms.controller;

import com.odin.wms.domain.enums.StorageType;
import com.odin.wms.dto.request.CreateProductWmsRequest;
import com.odin.wms.dto.request.UpdateProductWmsRequest;
import com.odin.wms.dto.response.ProductWmsResponse;
import com.odin.wms.service.ProductWmsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductWmsController {

    private final ProductWmsService productWmsService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ProductWmsResponse create(@Valid @RequestBody CreateProductWmsRequest request) {
        return productWmsService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public ProductWmsResponse findById(@PathVariable UUID id) {
        return productWmsService.findById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WMS_ADMIN', 'WMS_OPERATOR')")
    public List<ProductWmsResponse> findAll(
            @RequestParam(required = false) StorageType storageType,
            @RequestParam(required = false) Boolean active) {
        return productWmsService.findAll(storageType, active);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public ProductWmsResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateProductWmsRequest request) {
        return productWmsService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('WMS_ADMIN')")
    public void deactivate(@PathVariable UUID id) {
        productWmsService.deactivate(id);
    }
}
