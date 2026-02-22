package com.odin.wms.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AisleResponse(
        UUID id,
        UUID zoneId,
        String code,
        String name,
        Instant createdAt,
        Instant updatedAt
) {}
