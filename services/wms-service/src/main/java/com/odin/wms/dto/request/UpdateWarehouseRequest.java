package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWarehouseRequest(
        @NotBlank @Size(max = 255) String name,
        String address
) {}
