package com.odin.wms.dto.response;

import java.util.UUID;

/**
 * DTO de resposta para configuração de retenção LGPD (GET /audit/retention-config).
 */
public record RetentionConfigResponse(
        UUID tenantId,
        int retentionMonths,
        String retentionDescription
) {
    public static RetentionConfigResponse of(UUID tenantId, int months) {
        return new RetentionConfigResponse(tenantId, months, describeMonths(months));
    }

    private static String describeMonths(int months) {
        if (months % 12 == 0) {
            int years = months / 12;
            return years + " ano" + (years > 1 ? "s" : "") + (months == 84 ? " (padrão LGPD)" : "");
        }
        return months + " meses";
    }
}
