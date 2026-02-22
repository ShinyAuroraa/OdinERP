package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

public record ConfirmReceivingItemRequest(
        @Min(0) int receivedQuantity,
        String lotNumber,
        LocalDate manufacturingDate,
        LocalDate expiryDate,
        String gs1Code,
        List<String> serialNumbers
) {
}
