package com.odin.wms.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ApprovalSummaryResponse(
        UUID countId,
        int totalAdjusted,
        BigDecimal totalDivergenceQty,
        String status
) {}
