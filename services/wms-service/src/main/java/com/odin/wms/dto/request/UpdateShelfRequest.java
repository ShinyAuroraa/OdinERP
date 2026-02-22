package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateShelfRequest(
        @NotNull @Min(1) Integer level
) {}
