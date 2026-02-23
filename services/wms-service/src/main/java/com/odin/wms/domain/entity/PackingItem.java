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
@Table(name = "packing_items")
public class PackingItem extends BaseEntity {

    @Column(name = "packing_order_id", nullable = false)
    private UUID packingOrderId;

    @Column(name = "picking_item_id", nullable = false)
    private UUID pickingItemId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "quantity_packed", nullable = false)
    private int quantityPacked;

    @Builder.Default
    @Column(name = "scanned", nullable = false)
    private boolean scanned = false;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "scanned_by")
    private UUID scannedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
