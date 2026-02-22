package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.LocationType;

import java.time.Instant;
import java.util.UUID;

public record ZoneResponse(
        UUID id,
        UUID warehouseId,
        String code,
        String name,
        LocationType type,
        Boolean active,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
