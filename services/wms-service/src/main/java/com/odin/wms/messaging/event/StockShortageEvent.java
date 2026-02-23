package com.odin.wms.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockShortageEvent(
        String eventType,
        UUID tenantId,
        UUID productionOrderId,
        UUID requestId,
        List<ShortageItem> shortages,
        Instant detectedAt
) {
    public record ShortageItem(UUID productId, int quantityRequired, int quantityAvailable) {}
}
