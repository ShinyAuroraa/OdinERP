package com.odin.wms.messaging.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ShippingCompletedEvent(
        String eventType,
        UUID tenantId,
        UUID shippingOrderId,
        UUID packingOrderId,
        UUID pickingOrderId,
        UUID crmOrderId,
        UUID warehouseId,
        String carrierName,
        String trackingNumber,
        LocalDate estimatedDelivery,
        UUID operatorId,
        List<ShippingCompletedItem> items,
        Instant dispatchedAt
) {
    public record ShippingCompletedItem(
            UUID shippingItemId,
            UUID productId,
            UUID lotId,
            int quantityShipped
    ) {}
}
