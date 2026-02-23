package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
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
@Table(name = "inventory_counts")
public class InventoryCount extends BaseEntity {

    public enum CountType { CYCLIC, FULL }

    public enum InventoryCountStatus {
        DRAFT, IN_PROGRESS, RECONCILED, APPROVED, CLOSED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "count_type", nullable = false, length = 20)
    private CountType countType;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InventoryCountStatus status;

    @Column(name = "adjustment_threshold", nullable = false)
    private int adjustmentThreshold;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "created_by")
    private UUID createdBy;
}
