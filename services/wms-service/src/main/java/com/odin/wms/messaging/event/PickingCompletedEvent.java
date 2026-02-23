package com.odin.wms.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PickingCompletedEvent(
        String eventType,
        UUID tenantId,
        UUID pickingOrderId,
        UUID crmOrderId,
        String status,
        UUID warehouseId,
        UUID operatorId,
        List<PickingCompletedItem> items,
        Instant completedAt
) {
    public record PickingCompletedItem(
            UUID productId,
            UUID lotId,
            UUID locationId,
            int quantityRequested,
            int quantityPicked
    ) {}
}
