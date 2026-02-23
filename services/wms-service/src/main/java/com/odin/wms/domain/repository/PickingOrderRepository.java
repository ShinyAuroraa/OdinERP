package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.PickingOrder;
import com.odin.wms.domain.entity.PickingOrder.PickingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PickingOrderRepository extends JpaRepository<PickingOrder, UUID> {

    Page<PickingOrder> findByTenantIdAndStatus(UUID tenantId, PickingStatus status, Pageable pageable);

    Page<PickingOrder> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<PickingOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PickingOrder> findByTenantIdAndCrmOrderId(UUID tenantId, UUID crmOrderId);

    Page<PickingOrder> findByTenantIdAndOperatorId(UUID tenantId, UUID operatorId, Pageable pageable);

    Page<PickingOrder> findByTenantIdAndStatusAndOperatorId(UUID tenantId, PickingStatus status, UUID operatorId, Pageable pageable);
}
