package com.odin.wms.dto.response;

import java.util.UUID;

/**
 * Ocupação de uma zona por localização e capacidade.
 * Parte de {@link WarehouseOccupationResponse}.
 */
public record ZoneOccupationResponse(
        UUID zoneId,
        String zoneName,
        long totalLocations,
        long occupiedLocations,
        double occupancyRate,
        long totalCapacityUnits,
        long usedCapacityUnits
) {
}
