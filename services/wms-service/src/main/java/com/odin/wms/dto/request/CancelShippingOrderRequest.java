package com.odin.wms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelShippingOrderRequest(
        @NotBlank String cancellationReason
) {}
