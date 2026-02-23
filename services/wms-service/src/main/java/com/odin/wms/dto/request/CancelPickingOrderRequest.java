package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelPickingOrderRequest(
        @NotBlank String reason
) {}
