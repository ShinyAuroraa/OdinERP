package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.Shelf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShelfRepository extends JpaRepository<Shelf, UUID> {

    List<Shelf> findByTenantIdAndAisleId(UUID tenantId, UUID aisleId);

    boolean existsByTenantIdAndAisleIdAndCode(UUID tenantId, UUID aisleId, String code);
}
