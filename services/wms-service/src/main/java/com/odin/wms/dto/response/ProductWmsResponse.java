package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.StorageType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductWmsResponse(
        UUID id,
        UUID tenantId,
        UUID productId,
        String sku,
        String name,
        StorageType storageType,
        Boolean controlsLot,
        Boolean controlsSerial,
        Boolean controlsExpiry,
        String ean13,
        String gs1128,
        String qrCode,
        BigDecimal unitWidthCm,
        BigDecimal unitHeightCm,
        BigDecimal unitDepthCm,
        BigDecimal unitWeightKg,
        Integer unitsPerLocation,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
