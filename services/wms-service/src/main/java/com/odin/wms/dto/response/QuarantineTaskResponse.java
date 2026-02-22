package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.QuarantineDecision;
import com.odin.wms.domain.enums.QuarantineStatus;

import java.time.Instant;
import java.util.UUID;

public record QuarantineTaskResponse(
        UUID id,
        UUID tenantId,
        UUID receivingNoteId,
        UUID receivingNoteItemId,
        UUID stockItemId,
        UUID sourceLocationId,
        UUID quarantineLocationId,
        QuarantineStatus status,
        QuarantineDecision decision,
        String qualityNotes,
        UUID supervisorId,
        Instant startedAt,
        Instant decidedAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {
}
