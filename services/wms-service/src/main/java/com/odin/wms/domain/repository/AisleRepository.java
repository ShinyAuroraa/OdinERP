package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.Aisle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AisleRepository extends JpaRepository<Aisle, UUID> {

    List<Aisle> findByTenantIdAndZoneId(UUID tenantId, UUID zoneId);

    boolean existsByTenantIdAndZoneIdAndCode(UUID tenantId, UUID zoneId, String code);
}
