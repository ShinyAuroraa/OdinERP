package com.odin.wms.dto.response;

import com.odin.wms.domain.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta para listagem paginada de audit_log (GET /audit/log).
 * Não expõe oldValue/newValue/ipAddress/userAgent — disponíveis apenas em GET /audit/log/{id}.
 */
public record AuditLogEntryResponse(
        UUID id,
        UUID tenantId,
        String entityType,
        UUID entityId,
        String action,
        UUID actorId,
        String actorName,
        String actorRole,
        String correlationId,
        Instant createdAt
) {
    public static AuditLogEntryResponse from(AuditLog log) {
        return new AuditLogEntryResponse(
                log.getId(),
                log.getTenantId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getAction().name(),
                log.getActorId(),
                log.getActorName(),
                log.getActorRole(),
                log.getCorrelationId(),
                log.getCreatedAt()
        );
    }
}
