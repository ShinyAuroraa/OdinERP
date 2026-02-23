package com.odin.wms.dto.request;

public record SetPackageDetailsRequest(
        Double weightKg,
        String packageType,
        Double lengthCm,
        Double widthCm,
        Double heightCm,
        String notes
) {}
