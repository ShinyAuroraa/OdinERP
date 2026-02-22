package com.odin.wms.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Taxa de ocupação do armazém agregada por zona.
 * Retornado por GET /stock/occupation?warehouseId={id}.
 */
public record WarehouseOccupationResponse(
        UUID warehouseId,
        String warehouseName,
        long totalLocations,
        long occupiedLocations,
        double occupancyRate,
        List<ZoneOccupationResponse> zones
) {
}
