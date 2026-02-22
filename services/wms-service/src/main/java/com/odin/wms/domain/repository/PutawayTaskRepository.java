package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.PutawayTask;
import com.odin.wms.domain.enums.PutawayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PutawayTaskRepository extends JpaRepository<PutawayTask, UUID> {

    /**
     * Idempotência: verifica se já existem tarefas para a nota de recebimento.
     * Evita regeneração duplicada via POST /receiving-notes/{id}/putaway-tasks.
     */
    boolean existsByTenantIdAndReceivingNoteId(UUID tenantId, UUID receivingNoteId);

    /**
     * Lista todas as tarefas de um tenant — usado no findAll() com filtro in-memory por warehouse.
     */
    List<PutawayTask> findByTenantId(UUID tenantId);

    /**
     * Lista tarefas por nota de recebimento — usado em GET /putaway-tasks?receivingNoteId=.
     */
    List<PutawayTask> findByTenantIdAndReceivingNoteId(UUID tenantId, UUID receivingNoteId);

    /**
     * Lista tarefas por status — usado em GET /putaway-tasks?status=.
     */
    List<PutawayTask> findByTenantIdAndStatus(UUID tenantId, PutawayStatus status);

    /**
     * Isolamento multi-tenant: garante que a tarefa pertence ao tenant correto.
     */
    Optional<PutawayTask> findByIdAndTenantId(UUID id, UUID tenantId);
}
