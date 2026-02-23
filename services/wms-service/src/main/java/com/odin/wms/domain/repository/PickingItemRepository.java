package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.PickingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PickingItemRepository extends JpaRepository<PickingItem, UUID> {

    List<PickingItem> findByPickingOrderIdOrderBySortOrderAsc(UUID pickingOrderId);

    Optional<PickingItem> findByIdAndTenantId(UUID id, UUID tenantId);
}
