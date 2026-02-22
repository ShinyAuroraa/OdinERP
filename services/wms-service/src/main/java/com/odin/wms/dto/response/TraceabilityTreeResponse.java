package com.odin.wms.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Árvore completa de rastreabilidade do lote desde recebimento até expedição.
 * AC3 — GET /traceability/lot/{lotId}/tree
 */
public record TraceabilityTreeResponse(
        UUID lotId,
        String lotNumber,
        String productCode,
        LocalDate expiryDate,
        String supplier,
        Instant receivedAt,
        List<TraceabilityEvent> events,
        String currentStatus,
        int remainingQuantity
) {

    /**
     * Evento individual na árvore de rastreabilidade com sequência cronológica.
     */
    public record TraceabilityEvent(
            int sequence,
            String type,
            Instant timestamp,
            String location,
            int quantity,
            String agent
    ) {}
}
