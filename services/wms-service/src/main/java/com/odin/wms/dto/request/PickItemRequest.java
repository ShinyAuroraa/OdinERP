package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PickItemRequest(
        @NotNull @Min(0) Integer quantityPicked,
        @NotNull UUID pickedBy
) {}
