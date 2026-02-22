package com.odin.wms.dto.request;

import com.odin.wms.domain.enums.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateLocationRequest(
        @NotBlank @Size(max = 100) String code,
        @NotNull LocationType type,
        Integer capacityUnits,
        BigDecimal capacityWeightKg
) {}
