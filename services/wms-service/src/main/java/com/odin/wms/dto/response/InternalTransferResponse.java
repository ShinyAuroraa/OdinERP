package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.InternalTransfer;

import java.time.Instant;
import java.util.UUID;

public record InternalTransferResponse(
        UUID id,
        String transferType,
        String status,
        UUID sourceLocationId,
        UUID destinationLocationId,
        UUID productId,
        UUID lotId,
        int quantity,
        String reason,
        UUID requestedBy,
        UUID confirmedBy,
        UUID cancelledBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static InternalTransferResponse from(InternalTransfer t) {
        return new InternalTransferResponse(
                t.getId(),
                t.getTransferType().name(),
                t.getStatus().name(),
                t.getSourceLocation().getId(),
                t.getDestinationLocation().getId(),
                t.getProduct().getId(),
                t.getLot() != null ? t.getLot().getId() : null,
                t.getQuantity(),
                t.getReason(),
                t.getRequestedBy(),
                t.getConfirmedBy(),
                t.getCancelledBy(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
