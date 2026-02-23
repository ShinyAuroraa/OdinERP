package com.odin.wms.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record MrpProductionOrderCancelledEvent(
        String eventType,
        UUID tenantId,
        UUID productionOrderId,
        String cancellationReason,
        Instant cancelledAt
) {}
