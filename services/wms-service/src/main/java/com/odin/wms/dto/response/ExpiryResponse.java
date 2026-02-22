package com.odin.wms.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lote próximo do vencimento, ordenado por expiryDate ASC (FEFO).
 * AC4 — GET /traceability/product/{productId}/expiry
 *
 * daysUntilExpiry é null para produtos sem controle de validade (Lot.expiryDate = null).
 */
public record ExpiryResponse(
        UUID lotId,
        String lotNumber,
        LocalDate expiryDate,
        Integer daysUntilExpiry,
        int quantityAvailable,
        String locationCode,
        UUID warehouseId
) {}
