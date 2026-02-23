package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.InventoryCount.CountType;
import com.odin.wms.domain.entity.InventoryCount.InventoryCountStatus;

import java.time.Instant;
import java.util.UUID;

public record InventoryCountResponse(
        UUID countId,
        CountType countType,
        UUID warehouseId,
        UUID zoneId,
        InventoryCountStatus status,
        int adjustmentThreshold,
        long totalItems,
        Instant startedAt,
        Instant closedAt,
        Instant createdAt
) {}
