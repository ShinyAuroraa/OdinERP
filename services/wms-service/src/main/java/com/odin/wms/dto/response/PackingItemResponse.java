package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.PackingItem;

import java.time.Instant;
import java.util.UUID;

public record PackingItemResponse(
        UUID id,
        UUID packingOrderId,
        UUID pickingItemId,
        UUID productId,
        UUID lotId,
        int quantityPacked,
        boolean scanned,
        Instant scannedAt,
        UUID scannedBy
) {
    public static PackingItemResponse from(PackingItem item) {
        return new PackingItemResponse(
                item.getId(),
                item.getPackingOrderId(),
                item.getPickingItemId(),
                item.getProductId(),
                item.getLotId(),
                item.getQuantityPacked(),
                item.isScanned(),
                item.getScannedAt(),
                item.getScannedBy()
        );
    }
}
