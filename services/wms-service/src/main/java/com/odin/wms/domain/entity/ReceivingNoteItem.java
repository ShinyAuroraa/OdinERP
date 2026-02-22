package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import com.odin.wms.domain.enums.DivergenceType;
import com.odin.wms.domain.enums.ReceivingItemStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Item de conferência da nota de recebimento.
 * @Version herdado de BaseEntity via 'version' field — previne conflito concorrente.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "receiving_note_items")
public class ReceivingNoteItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_note_id", nullable = false)
    private ReceivingNote receivingNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductWms product;

    @Column(name = "expected_quantity", nullable = false)
    private int expectedQuantity;

    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "divergence_type", nullable = false, length = 20)
    @Builder.Default
    private DivergenceType divergenceType = DivergenceType.NONE;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "gs1_code", length = 200)
    private String gs1Code;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 20)
    @Builder.Default
    private ReceivingItemStatus itemStatus = ReceivingItemStatus.PENDING;

    /** Lote criado/associado após confirmação do item. Nullable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    /** StockItem criado no complete(). Nullable até a nota ser completada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_id")
    private StockItem stockItem;

    /** Optimistic locking — previne dois operadores confirmando o mesmo item simultaneamente. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
