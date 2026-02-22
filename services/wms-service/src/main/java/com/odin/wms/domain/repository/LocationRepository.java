package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.Location;
import com.odin.wms.domain.enums.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    Optional<Location> findByTenantIdAndCode(UUID tenantId, String code);

    List<Location> findByTenantIdAndType(UUID tenantId, LocationType type);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
