package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request para parsing de código de barras GS1.
 * AC5 — POST /gs1/parse
 *
 * format: EAN13 | GS1_128 | SSCC | QR_CODE
 */
public record GS1ParseRequest(
        @NotBlank String barcode,
        @NotBlank @Size(max = 20) String format
) {}
