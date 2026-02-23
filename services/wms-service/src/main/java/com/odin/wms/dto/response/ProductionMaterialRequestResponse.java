package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.ProductionMaterialRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductionMaterialRequestResponse(
        UUID id,
        UUID productionOrderId,
        String mrpOrderNumber,
        UUID warehouseId,
        String status,
        UUID pickingOrderId,
        int totalComponents,
        int shortageComponents,
        Instant confirmedDeliveryAt,
        UUID confirmedBy,
        Instant finishedGoodsReceivedAt,
        String cancellationReason,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        List<ProductionMaterialRequestItemResponse> items
) {
    public static ProductionMaterialRequestResponse from(
            ProductionMaterialRequest r,
            List<ProductionMaterialRequestItemResponse> items) {
        return new ProductionMaterialRequestResponse(
                r.getId(),
                r.getProductionOrderId(),
                r.getMrpOrderNumber(),
                r.getWarehouseId(),
                r.getStatus().name(),
                r.getPickingOrderId(),
                r.getTotalComponents(),
                r.getShortageComponents(),
                r.getConfirmedDeliveryAt(),
                r.getConfirmedBy(),
                r.getFinishedGoodsReceivedAt(),
                r.getCancellationReason(),
                r.getCancelledAt(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                items
        );
    }
}
