package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ShippingOrder;
import com.odin.wms.domain.entity.ShippingOrder.ShippingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShippingOrderRepository extends JpaRepository<ShippingOrder, UUID> {

    Optional<ShippingOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ShippingOrder> findByTenantIdAndPackingOrderId(UUID tenantId, UUID packingOrderId);

    Page<ShippingOrder> findByTenantIdAndStatus(UUID tenantId, ShippingStatus status, Pageable pageable);

    Page<ShippingOrder> findByTenantId(UUID tenantId, Pageable pageable);
}
