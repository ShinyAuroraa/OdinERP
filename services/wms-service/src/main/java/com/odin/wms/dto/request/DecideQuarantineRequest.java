package com.odin.wms.dto.request;

import com.odin.wms.domain.enums.QuarantineDecision;
import jakarta.validation.constraints.NotNull;

public record DecideQuarantineRequest(
        @NotNull QuarantineDecision decision,
        String qualityNotes
) {
}
