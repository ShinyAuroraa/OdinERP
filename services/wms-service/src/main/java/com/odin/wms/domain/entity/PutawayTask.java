package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import com.odin.wms.domain.enums.PutawayStatus;
import com.odin.wms.domain.enums.PutawayStrategy;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Tarefa de alocação (putaway) de um item recebido.
 * Uma tarefa por StockItem — idempotência garantida por uq_putaway_tasks_stock_item.
 * Motor FIFO/FEFO sugere localização; operador confirma ou escolhe outra.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "putaway_tasks")
public class PutawayTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_note_id", nullable = false)
    private ReceivingNote receivingNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    /** Dock de origem — localização RECEIVING_DOCK onde o item está. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_location_id", nullable = false)
    private Location sourceLocation;

    /** Localização sugerida pelo motor FIFO/FEFO. Sempre preenchida. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggested_location_id", nullable = false)
    private Location suggestedLocation;

    /**
     * Localização efetivamente confirmada pelo operador.
     * Pode diferir da sugestão. Preenchida somente no confirm().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_location_id")
    private Location confirmedLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PutawayStatus status = PutawayStatus.PENDING;

    /** Estratégia usada na sugestão de localização. */
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_used", nullable = false, length = 10)
    private PutawayStrategy strategyUsed;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Operador que executou a última ação. UUID cross-service (sem FK). */
    @Column(name = "operator_id")
    private UUID operatorId;

    /** Versão para optimistic locking — previne conflito entre dois operadores. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
