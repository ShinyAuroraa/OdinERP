package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Saldo de estoque por localização + produto + lote.
 * - received_at: chave para FIFO (First In, First Out)
 * - version: optimistic locking via @Version (JPA)
 * - Quantities CHECK constraints no banco: >= 0
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "stock_items")
public class StockItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductWms product;

    /** Nullable — produtos que não controlam lote não informam lot_id. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(name = "quantity_available", nullable = false)
    @Builder.Default
    private int quantityAvailable = 0;

    @Column(name = "quantity_reserved", nullable = false)
    @Builder.Default
    private int quantityReserved = 0;

    @Column(name = "quantity_quarantine", nullable = false)
    @Builder.Default
    private int quantityQuarantine = 0;

    @Column(name = "quantity_damaged", nullable = false)
    @Builder.Default
    private int quantityDamaged = 0;

    /** Chave para algoritmo FIFO. Imutável após inserção. */
    @Column(name = "received_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    /** Versão para optimistic locking. JPA incrementa automaticamente em cada UPDATE. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
