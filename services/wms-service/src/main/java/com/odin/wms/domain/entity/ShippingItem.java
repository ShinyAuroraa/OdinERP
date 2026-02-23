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
@Table(name = "shipping_items")
public class ShippingItem extends BaseEntity {

    @Column(name = "shipping_order_id", nullable = false)
    private UUID shippingOrderId;

    @Column(name = "packing_item_id", nullable = false)
    private UUID packingItemId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "quantity_shipped", nullable = false)
    private int quantityShipped;

    @Builder.Default
    @Column(name = "loaded", nullable = false)
    private boolean loaded = false;

    @Column(name = "loaded_at")
    private Instant loadedAt;

    @Column(name = "loaded_by")
    private UUID loadedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
