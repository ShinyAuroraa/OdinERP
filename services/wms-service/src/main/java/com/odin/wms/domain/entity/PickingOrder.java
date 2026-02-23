package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "picking_orders")
public class PickingOrder extends BaseEntity {

    public enum PickingStatus { PENDING, IN_PROGRESS, COMPLETED, PARTIAL, CANCELLED }
    public enum PickingType   { SINGLE, WAVE, BATCH }
    public enum RoutingAlgorithm { S_SHAPE, Z_SHAPE, LARGEST_GAP }

    @Column(name = "crm_order_id")
    private UUID crmOrderId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PickingStatus status = PickingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "picking_type", nullable = false, length = 20)
    @Builder.Default
    private PickingType pickingType = PickingType.SINGLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_algorithm", nullable = false, length = 20)
    @Builder.Default
    private RoutingAlgorithm routingAlgorithm = RoutingAlgorithm.S_SHAPE;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
