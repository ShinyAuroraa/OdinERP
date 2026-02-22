package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.PutawayStatus;
import com.odin.wms.domain.enums.PutawayStrategy;

import java.time.Instant;
import java.util.UUID;

public record PutawayTaskResponse(
        UUID id,
        UUID tenantId,
        UUID receivingNoteId,
        UUID stockItemId,
        UUID sourceLocationId,
        UUID suggestedLocationId,
        UUID confirmedLocationId,
        PutawayStatus status,
        PutawayStrategy strategyUsed,
        Instant startedAt,
        Instant confirmedAt,
        Instant cancelledAt,
        UUID operatorId,
        Instant createdAt,
        Instant updatedAt
) {
}
