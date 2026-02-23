package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "packing_orders")
public class PackingOrder extends BaseEntity {

    public enum PackingStatus { PENDING, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum PackageType   { BOX, ENVELOPE, PALLET, TUBE, OTHER }

    @Column(name = "picking_order_id", nullable = false)
    private UUID pickingOrderId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "crm_order_id")
    private UUID crmOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PackingStatus status = PackingStatus.PENDING;

    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", length = 20)
    private PackageType packageType;

    @Column(name = "length_cm", precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "sscc", length = 20, unique = true)
    private String sscc;

    @Column(name = "notes", length = 500)
    private String notes;

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
