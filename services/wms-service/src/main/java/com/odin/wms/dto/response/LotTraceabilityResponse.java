package com.odin.wms.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resposta do histórico completo de movimentações de um lote.
 * AC1 — GET /traceability/lot/{lotNumber}
 */
public record LotTraceabilityResponse(
        UUID lotId,
        String lotNumber,
        UUID productId,
        String productCode,
        LocalDate expiryDate,
        List<MovementItemResponse> movements,
        int totalMovements
) {

    /**
     * Item individual de movimentação dentro do histórico de lote/série.
     */
    public record MovementItemResponse(
            UUID id,
            String movementType,
            int quantity,
            UUID sourceLocationId,
            String sourceLocationCode,
            UUID destinationLocationId,
            String destinationLocationCode,
            String userId,
            UUID referenceId,
            String referenceType,
            java.time.Instant createdAt,
            String notes
    ) {}
}
