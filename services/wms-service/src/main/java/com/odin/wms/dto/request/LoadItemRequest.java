package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoadItemRequest(
        @NotBlank String barcode
) {}
