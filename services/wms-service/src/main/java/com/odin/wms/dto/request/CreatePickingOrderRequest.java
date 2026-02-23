package com.odin.wms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePickingOrderRequest(
        @NotNull UUID warehouseId,
        int priority,
        String routingAlgorithm,
        @NotNull @Valid @Size(min = 1) List<PickingOrderItemRequest> items
) {
    public record PickingOrderItemRequest(
            @NotNull UUID productId,
            int quantity,
            UUID lotId
    ) {}
}
