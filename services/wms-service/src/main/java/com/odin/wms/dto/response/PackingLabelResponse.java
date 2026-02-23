package com.odin.wms.dto.response;

public record PackingLabelResponse(
        String sscc,
        String barcodeBase64,
        String barcodeFormat
) {}
