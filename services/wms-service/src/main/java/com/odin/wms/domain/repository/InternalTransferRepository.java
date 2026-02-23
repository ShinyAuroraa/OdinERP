package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.InternalTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InternalTransferRepository extends JpaRepository<InternalTransfer, UUID> {

    Page<InternalTransfer> findByTenantIdAndStatus(
            UUID tenantId, InternalTransfer.TransferStatus status, Pageable pageable);

    Page<InternalTransfer> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<InternalTransfer> findByIdAndTenantId(UUID id, UUID tenantId);
}
