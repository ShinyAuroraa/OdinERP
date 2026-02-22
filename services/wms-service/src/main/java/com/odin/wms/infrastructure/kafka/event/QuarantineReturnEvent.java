package com.odin.wms.infrastructure.kafka.event;

import java.util.UUID;

/**
 * Evento Kafka publicado pelo WMS quando um item em quarentena é devolvido ao fornecedor.
 * Consumido pelo SCM para registrar a devolução física.
 *
 * Tópico: wms.quarantine.return-supplier
 */
public record QuarantineReturnEvent(
        UUID tenantId,
        UUID quarantineTaskId,
        UUID receivingNoteId,
        UUID stockItemId,
        UUID productId,
        int quantity,
        String qualityNotes
) {
}
