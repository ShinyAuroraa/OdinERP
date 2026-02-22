package com.odin.wms.dto.request;

import com.odin.wms.domain.enums.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateZoneRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull LocationType type
) {}
