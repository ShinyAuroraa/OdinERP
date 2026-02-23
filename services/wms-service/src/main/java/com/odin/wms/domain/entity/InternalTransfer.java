package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Representa uma transferência interna de estoque entre posições (locations).
 * Estado: PENDING → CONFIRMED | CANCELLED.
 * Estende BaseEntity (tem updatedAt) — status é mutável.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "internal_transfers")
public class InternalTransfer extends BaseEntity {

    public enum TransferType {
        MANUAL,
        /** Reservado para Story 5.1 — Picking / reabastecimento automático. */
        REPLENISHMENT
    }

    public enum TransferStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 20)
    private TransferType transferType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_location_id", nullable = false)
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private Location destinationLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductWms product;

    /** Nullable — produtos sem controle de lote. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** UUID Keycloak do operador que solicitou — sem FK cross-service. */
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    /** UUID Keycloak do operador que confirmou — preenchido na confirmação. */
    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    /** UUID Keycloak do supervisor que cancelou — preenchido no cancelamento. */
    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "reason", length = 500)
    private String reason;

    /** Optimistic locking no próprio InternalTransfer. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
