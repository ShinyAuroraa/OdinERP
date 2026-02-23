package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta completo para export regulatório (GET /audit/log/export e GET /audit/log/{id}).
 * Inclui todos os campos do AuditLog, incluindo oldValue, newValue, ipAddress, userAgent.
 */
public record AuditLogExportResponse(
        UUID id,
        UUID tenantId,
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        String actorName,
        String actorRole,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent,
        String correlationId,
        Instant createdAt
) {
    public static AuditLogExportResponse from(AuditLog log) {
        return new AuditLogExportResponse(
                log.getId(),
                log.getTenantId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction().name(),
                log.getActorId(),
                log.getActorName(),
                log.getActorRole(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCorrelationId(),
                log.getCreatedAt()
        );
    }
}
