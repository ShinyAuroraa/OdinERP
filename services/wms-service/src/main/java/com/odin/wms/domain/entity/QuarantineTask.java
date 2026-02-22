package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import com.odin.wms.domain.enums.QuarantineDecision;
import com.odin.wms.domain.enums.QuarantineStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Tarefa de controle de qualidade para itens FLAGGED de notas COMPLETED_WITH_DIVERGENCE.
 * Uma tarefa por StockItem — idempotência garantida por uq_quarantine_tasks_stock_item.
 * Workflow: PENDING → IN_REVIEW → APPROVED (RELEASE_TO_STOCK) / REJECTED (RETURN_TO_SUPPLIER, SCRAP) / CANCELLED.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "quarantine_tasks")
public class QuarantineTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_note_id", nullable = false)
    private ReceivingNote receivingNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_note_item_id", nullable = false)
    private ReceivingNoteItem receivingNoteItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    /** Dock de recebimento de origem — onde o item estava quando FLAGGED. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_location_id", nullable = false)
    private Location sourceLocation;

    /** Localização de quarentena para onde o item foi movido na geração. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quarantine_location_id", nullable = false)
    private Location quarantineLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private QuarantineStatus status = QuarantineStatus.PENDING;

    /**
     * Decisão do supervisor: RELEASE_TO_STOCK, RETURN_TO_SUPPLIER ou SCRAP.
     * Nullable até decide() ser chamado.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    private QuarantineDecision decision;

    /** Observações de qualidade do supervisor. Nullable. */
    @Column(name = "quality_notes", columnDefinition = "TEXT")
    private String qualityNotes;

    /** Supervisor que executou start() e/ou decide(). UUID cross-service (sem FK). */
    @Column(name = "supervisor_id")
    private UUID supervisorId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Versão para optimistic locking — previne dois supervisores decidindo a mesma task. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
