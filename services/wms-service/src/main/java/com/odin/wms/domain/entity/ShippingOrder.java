package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "shipping_orders")
public class ShippingOrder extends BaseEntity {

    public enum ShippingStatus { PENDING, IN_PROGRESS, DISPATCHED, DELIVERED, CANCELLED }

    @Column(name = "packing_order_id", nullable = false)
    private UUID packingOrderId;

    @Column(name = "picking_order_id")
    private UUID pickingOrderId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "crm_order_id")
    private UUID crmOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ShippingStatus status = ShippingStatus.PENDING;

    @Column(name = "carrier_name", length = 200)
    private String carrierName;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "driver_name", length = 200)
    private String driverName;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(name = "manifest_json", columnDefinition = "TEXT")
    private String manifestJson;

    @Column(name = "manifest_generated_at")
    private Instant manifestGeneratedAt;

    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
