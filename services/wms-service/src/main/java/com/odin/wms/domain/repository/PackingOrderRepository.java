package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.PackingOrder;
import com.odin.wms.domain.entity.PackingOrder.PackingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PackingOrderRepository extends JpaRepository<PackingOrder, UUID> {

    Optional<PackingOrder> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<PackingOrder> findByTenantIdAndPickingOrderId(UUID tenantId, UUID pickingOrderId);

    Page<PackingOrder> findByTenantId(UUID tenantId, Pageable pageable);

    Page<PackingOrder> findByTenantIdAndStatus(UUID tenantId, PackingStatus status, Pageable pageable);
}
