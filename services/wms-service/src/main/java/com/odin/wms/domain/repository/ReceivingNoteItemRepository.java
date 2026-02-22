package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.ReceivingNoteItem;
import com.odin.wms.domain.enums.ReceivingItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivingNoteItemRepository extends JpaRepository<ReceivingNoteItem, UUID> {

    List<ReceivingNoteItem> findByReceivingNoteId(UUID noteId);

    Optional<ReceivingNoteItem> findByReceivingNoteIdAndId(UUID noteId, UUID itemId);

    /**
     * Conta itens pendentes de confirmação — usado para verificar se a nota pode ser concluída.
     * Retorna 0 quando todos os itens já foram confirmados ou marcados como FLAGGED.
     */
    long countByReceivingNoteIdAndItemStatus(UUID noteId, ReceivingItemStatus status);
}
