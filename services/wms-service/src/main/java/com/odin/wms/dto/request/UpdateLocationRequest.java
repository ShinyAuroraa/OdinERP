package com.odin.wms.dto.request;

import com.odin.wms.domain.enums.LocationType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateLocationRequest(
        @NotNull LocationType type,
        Integer capacityUnits,
        BigDecimal capacityWeightKg
) {}
