package br.com.odin.crm.infrastructure.persistence.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, UUID> {
}
