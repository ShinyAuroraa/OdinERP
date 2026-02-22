package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.SerialNumber;
import com.odin.wms.domain.enums.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SerialNumberRepository extends JpaRepository<SerialNumber, UUID> {

    Optional<SerialNumber> findByTenantIdAndProductIdAndSerialNumber(
            UUID tenantId, UUID productId, String serialNumber);

    List<SerialNumber> findByTenantIdAndProductIdAndStatus(
            UUID tenantId, UUID productId, SerialStatus status);

    boolean existsByTenantIdAndProductIdAndSerialNumber(
            UUID tenantId, UUID productId, String serialNumber);
}
