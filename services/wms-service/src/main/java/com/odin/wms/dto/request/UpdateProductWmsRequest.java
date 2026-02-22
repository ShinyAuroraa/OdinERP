package com.odin.wms.dto.request;

import com.odin.wms.domain.enums.StorageType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateProductWmsRequest(
        @NotBlank String name,
        @NotNull StorageType storageType,
        Boolean controlsLot,
        Boolean controlsSerial,
        Boolean controlsExpiry,
        @Size(max = 13) String ean13,
        String gs1128,
        String qrCode,
        @DecimalMin("0.0") BigDecimal unitWidthCm,
        @DecimalMin("0.0") BigDecimal unitHeightCm,
        @DecimalMin("0.0") BigDecimal unitDepthCm,
        @DecimalMin("0.0") BigDecimal unitWeightKg,
        @Min(1) Integer unitsPerLocation
) {}
