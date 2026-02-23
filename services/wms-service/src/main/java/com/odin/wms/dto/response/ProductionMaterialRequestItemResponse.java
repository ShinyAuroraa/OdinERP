package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.ProductionMaterialRequestItem;

import java.time.Instant;
import java.util.UUID;

public record ProductionMaterialRequestItemResponse(
        UUID id,
        UUID productId,
        UUID lotId,
        UUID locationId,
        int quantityRequested,
        int quantityReserved,
        int quantityDelivered,
        boolean shortage,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductionMaterialRequestItemResponse from(ProductionMaterialRequestItem item) {
        return new ProductionMaterialRequestItemResponse(
                item.getId(),
                item.getProductId(),
                item.getLotId(),
                item.getLocationId(),
                item.getQuantityRequested(),
                item.getQuantityReserved(),
                item.getQuantityDelivered(),
                item.isShortage(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
