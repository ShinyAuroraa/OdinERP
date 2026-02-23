package com.odin.wms.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento Kafka publicado quando uma transferência interna é confirmada.
 * Tópico: wms.stock.movements
 * Key: tenantId (garante ordenação por tenant)
 */
public record InternalTransferConfirmedEvent(
        String eventType,
        UUID tenantId,
        UUID transferId,
        UUID movementId,
        UUID productId,
        UUID lotId,
        int quantity,
        UUID sourceLocationId,
        UUID destinationLocationId,
        UUID confirmedBy,
        Instant occurredAt
) {
}
