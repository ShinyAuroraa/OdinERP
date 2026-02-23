package com.odin.wms.dto.request;

import jakarta.validation.constraints.Min;

/**
 * DTO de request para PUT /audit/retention-config.
 * Valida que retentionMonths >= 12 (compliance LGPD mínimo).
 */
public record UpdateRetentionConfigRequest(
        @Min(value = 12, message = "Retenção mínima: 12 meses (compliance LGPD)")
        int retentionMonths
) {
}
