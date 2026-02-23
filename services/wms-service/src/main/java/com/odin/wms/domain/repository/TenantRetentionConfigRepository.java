package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.TenantRetentionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRetentionConfigRepository extends JpaRepository<TenantRetentionConfig, UUID> {

    Optional<TenantRetentionConfig> findByTenantId(UUID tenantId);
}
