package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.QuarantineTask;
import com.odin.wms.domain.enums.QuarantineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuarantineTaskRepository extends JpaRepository<QuarantineTask, UUID> {

    /**
     * Idempotência: verifica se já existem tasks de quarentena para a nota de recebimento.
     * Evita regeneração duplicada via POST /receiving-notes/{id}/quarantine-tasks.
     */
    boolean existsByTenantIdAndReceivingNoteId(UUID tenantId, UUID receivingNoteId);

    /**
     * Isolamento multi-tenant: garante que a tarefa pertence ao tenant correto.
     */
    Optional<QuarantineTask> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Lista todas as tasks de quarentena do tenant.
     */
    List<QuarantineTask> findByTenantId(UUID tenantId);

    /**
     * Lista tasks por status — usado em GET /quarantine-tasks?status=.
     */
    List<QuarantineTask> findByTenantIdAndStatus(UUID tenantId, QuarantineStatus status);

    /**
     * Lista tasks por warehouse — evita filtro in-memory N+1 em findAll().
     * Navega pela hierarquia: quarantineLocation → shelf → aisle → zone → warehouse.
     */
    @Query("SELECT qt FROM QuarantineTask qt " +
           "WHERE qt.tenantId = :tenantId " +
           "AND qt.quarantineLocation.shelf.aisle.zone.warehouse.id = :warehouseId")
    List<QuarantineTask> findByTenantIdAndWarehouseId(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId);

    /**
     * Lista tasks por status e warehouse — evita filtro in-memory N+1 em findAll() com ambos os filtros.
     */
    @Query("SELECT qt FROM QuarantineTask qt " +
           "WHERE qt.tenantId = :tenantId " +
           "AND qt.status = :status " +
           "AND qt.quarantineLocation.shelf.aisle.zone.warehouse.id = :warehouseId")
    List<QuarantineTask> findByTenantIdAndStatusAndWarehouseId(
            @Param("tenantId") UUID tenantId,
            @Param("status") QuarantineStatus status,
            @Param("warehouseId") UUID warehouseId);
}
