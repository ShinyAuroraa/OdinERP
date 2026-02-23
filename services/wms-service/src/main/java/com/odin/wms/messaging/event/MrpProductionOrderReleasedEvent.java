package com.odin.wms.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MrpProductionOrderReleasedEvent(
        String eventType,
        UUID tenantId,
        UUID productionOrderId,
        String mrpOrderNumber,
        UUID warehouseId,
        List<ProductionComponent> components,
        Instant releasedAt
) {
    public record ProductionComponent(
            UUID productId,
            int quantityRequired
    ) {}
}
