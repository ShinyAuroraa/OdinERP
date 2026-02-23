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
@Table(name = "production_material_requests")
public class ProductionMaterialRequest extends BaseEntity {

    public enum MaterialRequestStatus {
        PENDING, RESERVING, STOCK_SHORTAGE, PICKING_PENDING,
        PICKING_IN_PROGRESS, DELIVERED, FINISHED_GOODS_RECEIVED, CANCELLED, ERROR
    }

    @Column(name = "production_order_id", nullable = false)
    private UUID productionOrderId;

    @Column(name = "mrp_order_number", length = 100)
    private String mrpOrderNumber;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private MaterialRequestStatus status = MaterialRequestStatus.PENDING;

    @Column(name = "picking_order_id")
    private UUID pickingOrderId;

    @Column(name = "total_components", nullable = false)
    @Builder.Default
    private int totalComponents = 0;

    @Column(name = "shortage_components", nullable = false)
    @Builder.Default
    private int shortageComponents = 0;

    @Column(name = "confirmed_delivery_at")
    private Instant confirmedDeliveryAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "finished_goods_received_at")
    private Instant finishedGoodsReceivedAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
