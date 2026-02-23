package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ShippingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShippingItemRepository extends JpaRepository<ShippingItem, UUID> {

    List<ShippingItem> findByTenantIdAndShippingOrderId(UUID tenantId, UUID shippingOrderId);

    Optional<ShippingItem> findByIdAndTenantId(UUID id, UUID tenantId);
}
