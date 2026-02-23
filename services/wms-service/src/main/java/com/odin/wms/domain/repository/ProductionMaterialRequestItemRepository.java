package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ProductionMaterialRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductionMaterialRequestItemRepository extends JpaRepository<ProductionMaterialRequestItem, UUID> {

    List<ProductionMaterialRequestItem> findByTenantIdAndRequestId(UUID tenantId, UUID requestId);
}
