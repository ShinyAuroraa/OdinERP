package com.odin.wms.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FinishedGoodsReceivedEvent(
        String eventType,
        UUID tenantId,
        UUID productionOrderId,
        UUID requestId,
        UUID warehouseId,
        List<ReceivedItem> items,
        UUID receivedBy,
        Instant receivedAt
) {
    public record ReceivedItem(UUID productId, UUID locationId, int quantityReceived) {}
}
