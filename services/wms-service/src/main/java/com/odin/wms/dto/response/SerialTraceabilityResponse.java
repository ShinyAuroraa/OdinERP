package com.odin.wms.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Resposta do histórico completo de movimentações de um número de série.
 * AC2 — GET /traceability/serial/{serialNumber}
 */
public record SerialTraceabilityResponse(
        UUID serialNumberId,
        String serialNumber,
        UUID productId,
        String productCode,
        String currentLocationCode,
        String currentStatus,
        List<LotTraceabilityResponse.MovementItemResponse> movements
) {}
