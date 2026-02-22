package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    List<Zone> findByTenantIdAndWarehouseId(UUID tenantId, UUID warehouseId);

    boolean existsByTenantIdAndWarehouseIdAndCode(UUID tenantId, UUID warehouseId, String code);
}
