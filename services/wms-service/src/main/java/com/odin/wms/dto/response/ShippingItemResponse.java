package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.ShippingItem;

import java.time.Instant;
import java.util.UUID;

public record ShippingItemResponse(
        UUID id,
        UUID packingItemId,
        UUID productId,
        UUID lotId,
        int quantityShipped,
        boolean loaded,
        Instant loadedAt,
        UUID loadedBy
) {
    public static ShippingItemResponse from(ShippingItem item) {
        return new ShippingItemResponse(
                item.getId(),
                item.getPackingItemId(),
                item.getProductId(),
                item.getLotId(),
                item.getQuantityShipped(),
                item.isLoaded(),
                item.getLoadedAt(),
                item.getLoadedBy()
        );
    }
}
