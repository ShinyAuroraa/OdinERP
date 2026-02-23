package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.PackingOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PackingOrderResponse(
        UUID id,
        UUID tenantId,
        UUID pickingOrderId,
        UUID warehouseId,
        UUID crmOrderId,
        String status,
        UUID operatorId,
        BigDecimal weightKg,
        String packageType,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String sscc,
        String notes,
        Instant completedAt,
        Instant cancelledAt,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt,
        List<PackingItemResponse> items
) {
    public static PackingOrderResponse from(PackingOrder order, List<PackingItemResponse> items) {
        return new PackingOrderResponse(
                order.getId(),
                order.getTenantId(),
                order.getPickingOrderId(),
                order.getWarehouseId(),
                order.getCrmOrderId(),
                order.getStatus().name(),
                order.getOperatorId(),
                order.getWeightKg(),
                order.getPackageType() != null ? order.getPackageType().name() : null,
                order.getLengthCm(),
                order.getWidthCm(),
                order.getHeightCm(),
                order.getSscc(),
                order.getNotes(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCancellationReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }
}
