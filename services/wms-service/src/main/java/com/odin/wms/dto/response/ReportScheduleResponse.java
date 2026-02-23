package com.odin.wms.dto.response;

import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.domain.enums.ReportType;

import java.time.Instant;
import java.util.UUID;

public record ReportScheduleResponse(
        UUID id,
        UUID tenantId,
        ReportType reportType,
        ExportFormat exportFormat,
        String cronExpression,
        UUID warehouseId,
        boolean active,
        Instant lastExecutedAt,
        Instant nextExecutionAt,
        Instant createdAt
) {}
