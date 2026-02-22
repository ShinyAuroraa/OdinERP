package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.QuarantineDecision;
import com.odin.wms.domain.enums.QuarantineStatus;

import java.time.Instant;
import java.util.UUID;

public record QuarantineTaskSummaryResponse(
        UUID id,
        UUID receivingNoteId,
        UUID stockItemId,
        UUID quarantineLocationId,
        QuarantineStatus status,
        QuarantineDecision decision,
        Instant createdAt
) {
}
