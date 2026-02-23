package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.InventoryCountItem.ItemCountStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryCountItemResponse(
        UUID itemId,
        String locationCode,
        String productCode,
        String lotNumber,
        LocalDate expiryDate,
        BigDecimal expectedQty,
        BigDecimal countedQty,
        BigDecimal divergenceQty,
        BigDecimal divergencePct,
        ItemCountStatus status
) {}
