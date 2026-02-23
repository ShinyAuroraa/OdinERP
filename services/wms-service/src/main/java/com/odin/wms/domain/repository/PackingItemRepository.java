package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.PackingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PackingItemRepository extends JpaRepository<PackingItem, UUID> {

    List<PackingItem> findByTenantIdAndPackingOrderId(UUID tenantId, UUID packingOrderId);

    Optional<PackingItem> findByTenantIdAndId(UUID tenantId, UUID id);
}
