package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "production_material_request_items")
public class ProductionMaterialRequestItem extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "quantity_requested", nullable = false)
    private int quantityRequested;

    @Column(name = "quantity_reserved", nullable = false)
    @Builder.Default
    private int quantityReserved = 0;

    @Column(name = "quantity_delivered", nullable = false)
    @Builder.Default
    private int quantityDelivered = 0;

    @Column(name = "shortage", nullable = false)
    @Builder.Default
    private boolean shortage = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
