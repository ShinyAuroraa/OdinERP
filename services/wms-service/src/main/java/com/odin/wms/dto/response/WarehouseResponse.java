package com.odin.wms.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String code,
        String name,
        String address,
        Boolean active,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
