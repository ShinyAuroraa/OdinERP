package com.odin.wms.infrastructure.kafka.event;

import java.util.UUID;

public record PurchaseOrderItem(
        UUID productId,
        int expectedQuantity
) {
}
