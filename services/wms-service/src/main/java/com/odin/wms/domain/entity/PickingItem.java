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
@Table(name = "picking_items")
public class PickingItem extends BaseEntity {

    public enum PickingItemStatus { PENDING, PICKED, PARTIAL, SKIPPED }

    @Column(name = "picking_order_id", nullable = false)
    private UUID pickingOrderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "quantity_requested", nullable = false)
    private int quantityRequested;

    @Column(name = "quantity_picked", nullable = false)
    @Builder.Default
    private int quantityPicked = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PickingItemStatus status = PickingItemStatus.PENDING;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "picked_by")
    private UUID pickedBy;

    @Column(name = "picked_at")
    private Instant pickedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
