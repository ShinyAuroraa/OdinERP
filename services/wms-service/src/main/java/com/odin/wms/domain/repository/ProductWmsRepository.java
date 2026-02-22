package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ProductWms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductWmsRepository extends JpaRepository<ProductWms, UUID> {

    Optional<ProductWms> findByTenantIdAndSku(UUID tenantId, String sku);

    Optional<ProductWms> findByTenantIdAndEan13(UUID tenantId, String ean13);

    boolean existsByTenantIdAndSku(UUID tenantId, String sku);
}
