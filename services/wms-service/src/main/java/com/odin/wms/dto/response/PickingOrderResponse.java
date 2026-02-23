package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.PickingOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PickingOrderResponse(
        UUID id,
        String status,
        String pickingType,
        String routingAlgorithm,
        int priority,
        UUID warehouseId,
        UUID crmOrderId,
        UUID operatorId,
        UUID zoneId,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancellationReason,
        List<PickingItemResponse> items
) {
    public static PickingOrderResponse from(PickingOrder order, List<PickingItemResponse> items) {
        return new PickingOrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getPickingType().name(),
                order.getRoutingAlgorithm().name(),
                order.getPriority(),
                order.getWarehouseId(),
                order.getCrmOrderId(),
                order.getOperatorId(),
                order.getZoneId(),
                order.getCreatedBy(),
                order.getCreatedAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                items
        );
    }
}
