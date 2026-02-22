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

    Page<AuditLog> findByTenantIdAndEntityTypeAndEntityId(
            UUID tenantId, String entityType, UUID entityId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndActorIdAndCreatedAtBetween(
            UUID tenantId, UUID actorId, Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByTenantIdAndAction(UUID tenantId, AuditAction action, Pageable pageable);
}
