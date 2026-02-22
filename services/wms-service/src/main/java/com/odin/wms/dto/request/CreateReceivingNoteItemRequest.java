package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReceivingNoteItemRequest(
        @NotNull UUID productId,
        @Min(1) int expectedQuantity
) {
}
