package com.odin.wms.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ShelfResponse(
        UUID id,
        UUID aisleId,
        String code,
        Integer level,
        Instant createdAt,
        Instant updatedAt
) {}
