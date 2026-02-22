package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseAppendOnlyEntity;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Registro imutável de movimentação de estoque (append-only).
 * NÃO possui updatedAt — registros nunca são modificados após inserção.
 * kafka_event_id garante idempotência em reprocessamento de eventos.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "stock_movements")
public class StockMovement extends BaseAppendOnlyEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private MovementType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductWms product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serial_number_id")
    private SerialNumber serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id")
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private Location destinationLocation;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 50)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    /** UUID do usuário Keycloak — sem FK cross-service. */
    @Column(name = "operator_id", nullable = false)
    private UUID operatorId;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /** ID do evento Kafka que originou este movimento (idempotência). */
    @Column(name = "kafka_event_id")
    private String kafkaEventId;
}
