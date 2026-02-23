package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubstituteLocationRequest(
        @NotNull UUID alternativeLocationId,
        UUID alternativeLotId,
        @NotNull @Min(1) Integer quantityPicked,
        @NotNull UUID pickedBy
) {}
