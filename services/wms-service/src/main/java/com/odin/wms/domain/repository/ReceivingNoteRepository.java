package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ReceivingNote;
import com.odin.wms.domain.enums.ReceivingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivingNoteRepository extends JpaRepository<ReceivingNote, UUID> {

    List<ReceivingNote> findByTenantId(UUID tenantId);

    List<ReceivingNote> findByTenantIdAndStatus(UUID tenantId, ReceivingStatus status);

    List<ReceivingNote> findByTenantIdAndWarehouseId(UUID tenantId, UUID warehouseId);

    /**
     * Idempotência Kafka: verifica se PO já foi processado para o tenant.
     */
    boolean existsByTenantIdAndPurchaseOrderRef(UUID tenantId, String purchaseOrderRef);

    /**
     * Isolamento multi-tenant: garante que a nota pertence ao tenant correto.
     */
    Optional<ReceivingNote> findByIdAndTenantId(UUID id, UUID tenantId);
}
