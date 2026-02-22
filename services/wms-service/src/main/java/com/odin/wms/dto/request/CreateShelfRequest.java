package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShelfRequest(
        @NotBlank @Size(max = 50) String code,
        @Min(1) Integer level
) {}
