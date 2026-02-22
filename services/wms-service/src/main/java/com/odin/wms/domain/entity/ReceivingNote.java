package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import com.odin.wms.domain.enums.ReceivingStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Nota de recebimento de mercadorias.
 * Criada manualmente (REST) ou via Kafka consumer (SCM purchase order confirmed).
 * purchase_order_ref é utilizado para idempotência no consumer Kafka.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "receiving_notes")
public class ReceivingNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dock_location_id", nullable = false)
    private Location dockLocation;

    /** Referência ao PO no SCM — sem FK cross-service. Garante idempotência Kafka. */
    @Column(name = "purchase_order_ref", length = 100)
    private String purchaseOrderRef;

    /** UUID do fornecedor — referência cross-service sem FK. */
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ReceivingStatus status = ReceivingStatus.PENDING;

    @OneToMany(mappedBy = "receivingNote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceivingNoteItem> items = new ArrayList<>();
}
