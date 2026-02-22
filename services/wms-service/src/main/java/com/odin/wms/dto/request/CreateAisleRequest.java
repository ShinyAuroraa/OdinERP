package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAisleRequest(
        @NotBlank @Size(max = 50) String code,
        @Size(max = 255) String name
) {}
