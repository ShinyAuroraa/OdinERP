package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.InventoryCount;
import com.odin.wms.domain.entity.InventoryCount.InventoryCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {

    Optional<InventoryCount> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<InventoryCount> findByTenantIdAndStatus(UUID tenantId, InventoryCountStatus status, Pageable pageable);
}
