package com.odin.wms.messaging.event;

import java.util.List;
import java.util.UUID;

public record CrmOrderConfirmedEvent(
        String eventType,
        UUID tenantId,
        UUID crmOrderId,
        UUID warehouseId,
        int priority,
        List<CrmOrderItem> items
) {
    public record CrmOrderItem(
            UUID productId,
            int quantity
    ) {}
}
