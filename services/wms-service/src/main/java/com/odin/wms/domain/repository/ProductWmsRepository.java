package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ProductWms;
import com.odin.wms.domain.enums.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductWmsRepository extends JpaRepository<ProductWms, UUID> {

    Optional<ProductWms> findByTenantIdAndSku(UUID tenantId, String sku);

    Optional<ProductWms> findByTenantIdAndEan13(UUID tenantId, String ean13);

    boolean existsByTenantIdAndSku(UUID tenantId, String sku);

    boolean existsByTenantIdAndEan13(UUID tenantId, String ean13);

    List<ProductWms> findByTenantId(UUID tenantId);

    List<ProductWms> findByTenantIdAndStorageType(UUID tenantId, StorageType storageType);

    List<ProductWms> findByTenantIdAndActive(UUID tenantId, Boolean active);
}
