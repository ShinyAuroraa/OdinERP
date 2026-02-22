package com.odin.wms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateReceivingNoteRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID dockLocationId,
        UUID supplierId,
        String purchaseOrderRef,
        @NotEmpty @Valid List<CreateReceivingNoteItemRequest> items
) {
}
