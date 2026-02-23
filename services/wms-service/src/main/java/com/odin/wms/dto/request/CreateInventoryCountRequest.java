package com.odin.wms.dto.request;

import com.odin.wms.domain.entity.InventoryCount.CountType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInventoryCountRequest(
        @NotNull CountType countType,
        @NotNull UUID warehouseId,
        UUID zoneId,
        @Min(0) @Max(100) int adjustmentThreshold
) {}
