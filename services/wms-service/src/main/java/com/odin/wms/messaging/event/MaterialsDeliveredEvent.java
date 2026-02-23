package com.odin.wms.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MaterialsDeliveredEvent(
        String eventType,
        UUID tenantId,
        UUID productionOrderId,
        UUID requestId,
        UUID warehouseId,
        List<DeliveredItem> items,
        UUID confirmedBy,
        Instant deliveredAt
) {
    public record DeliveredItem(UUID productId, UUID lotId, int quantityDelivered) {}
}
