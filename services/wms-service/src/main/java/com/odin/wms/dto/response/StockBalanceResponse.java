package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.LocationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Saldo de estoque por StockItem (produto + localização + lote).
 * Retornado por GET /stock/balance e GET /stock/balance/location/{locationId}.
 */
public record StockBalanceResponse(
        UUID id,
        UUID productId,
        String productCode,
        String productName,
        UUID locationId,
        String locationCode,
        LocationType locationType,
        UUID lotId,
        String lotNumber,
        int quantityAvailable,
        int quantityReserved,
        int quantityQuarantine,
        int quantityDamaged,
        Instant receivedAt
) {
}
