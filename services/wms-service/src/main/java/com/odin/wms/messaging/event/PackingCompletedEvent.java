package com.odin.wms.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PackingCompletedEvent(
        String eventType,
        UUID tenantId,
        UUID packingOrderId,
        UUID pickingOrderId,
        UUID crmOrderId,
        UUID warehouseId,
        UUID operatorId,
        String sscc,
        BigDecimal weightKg,
        String packageType,
        String status,
        List<PackingCompletedItem> items,
        Instant completedAt
) {
    public record PackingCompletedItem(
            UUID productId,
            UUID lotId,
            int quantityPacked
    ) {}
}
