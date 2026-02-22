package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.DivergenceType;
import com.odin.wms.domain.enums.ReceivingItemStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ReceivingNoteItemResponse(
        UUID id,
        UUID productId,
        int expectedQuantity,
        Integer receivedQuantity,
        DivergenceType divergenceType,
        ReceivingItemStatus itemStatus,
        String lotNumber,
        LocalDate expiryDate,
        String gs1Code,
        UUID lotId,
        UUID stockItemId
) {
}
