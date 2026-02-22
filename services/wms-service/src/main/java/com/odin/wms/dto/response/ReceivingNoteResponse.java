package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.ReceivingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceivingNoteResponse(
        UUID id,
        UUID tenantId,
        UUID warehouseId,
        UUID dockLocationId,
        String purchaseOrderRef,
        UUID supplierId,
        ReceivingStatus status,
        List<ReceivingNoteItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
