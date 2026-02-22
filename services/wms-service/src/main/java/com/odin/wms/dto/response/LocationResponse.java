package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.LocationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        UUID shelfId,
        String code,
        String fullAddress,
        LocationType type,
        Integer capacityUnits,
        BigDecimal capacityWeightKg,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
