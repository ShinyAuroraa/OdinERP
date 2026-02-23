package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.PickingItem;

import java.time.Instant;
import java.util.UUID;

public record PickingItemResponse(
        UUID id,
        UUID productId,
        UUID lotId,
        UUID locationId,
        int quantityRequested,
        int quantityPicked,
        String status,
        int sortOrder,
        UUID pickedBy,
        Instant pickedAt
) {
    public static PickingItemResponse from(PickingItem item) {
        return new PickingItemResponse(
                item.getId(),
                item.getProductId(),
                item.getLotId(),
                item.getLocationId(),
                item.getQuantityRequested(),
                item.getQuantityPicked(),
                item.getStatus().name(),
                item.getSortOrder(),
                item.getPickedBy(),
                item.getPickedAt()
        );
    }
}
