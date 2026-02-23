package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ProductionMaterialRequest;
import com.odin.wms.domain.entity.ProductionMaterialRequest.MaterialRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionMaterialRequestRepository extends JpaRepository<ProductionMaterialRequest, UUID> {

    Optional<ProductionMaterialRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ProductionMaterialRequest> findByTenantIdAndProductionOrderId(UUID tenantId, UUID productionOrderId);

    boolean existsByTenantIdAndProductionOrderId(UUID tenantId, UUID productionOrderId);

    Page<ProductionMaterialRequest> findByTenantIdAndStatus(UUID tenantId, MaterialRequestStatus status, Pageable pageable);

    Page<ProductionMaterialRequest> findByTenantId(UUID tenantId, Pageable pageable);
}
