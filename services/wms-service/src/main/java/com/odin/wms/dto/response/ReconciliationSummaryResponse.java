package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.InventoryCount.InventoryCountStatus;

public record ReconciliationSummaryResponse(
        long totalItems,
        long autoApproved,
        long pendingApproval,
        long noVariance,
        InventoryCountStatus status
) {}
