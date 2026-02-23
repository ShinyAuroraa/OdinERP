package com.odin.wms.dto.request;

import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.domain.enums.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record ReportScheduleRequest(
        @NotNull ReportType reportType,
        @NotNull ExportFormat format,
        @NotBlank String cronExpression,
        @NotNull UUID warehouseId,
        Map<String, String> filters
) {}
