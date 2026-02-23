package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.AuditLog;
import com.odin.wms.domain.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // ── Métodos originais (Story 1.2) ────────────────────────────────────────

    Page<AuditLog> findByTenantIdAndEntityTypeAndEntityId(
            UUID tenantId, String entityType, UUID entityId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndActorIdAndCreatedAtBetween(
            UUID tenantId, UUID actorId, Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByTenantIdAndAction(UUID tenantId, AuditAction action, Pageable pageable);

    // ── Métodos adicionais (Story 4.4 — export regulatório) ──────────────────

    /** Listagem geral por tenant sem filtro de data — usada em GET /audit/log sem parâmetros. */
    Page<AuditLog> findByTenantId(UUID tenantId, Pageable pageable);

    /** Export por range de data — max 90 dias validado no service. */
    Page<AuditLog> findByTenantIdAndCreatedAtBetween(
            UUID tenantId, Instant from, Instant to, Pageable pageable);

    /** Filtro combinado: tipo de entidade + range de data. */
    Page<AuditLog> findByTenantIdAndEntityTypeAndCreatedAtBetween(
            UUID tenantId, String entityType, Instant from, Instant to, Pageable pageable);

    /** Contagem por tenant — verificação de integridade. */
    long countByTenantId(UUID tenantId);
}
