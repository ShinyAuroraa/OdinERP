package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "inventory_count_items")
public class InventoryCountItem extends BaseEntity {

    public enum ItemCountStatus {
        PENDING, COUNTED, SECOND_COUNTED, AUTO_APPROVED, PENDING_APPROVAL, ADJUSTED
    }

    @Column(name = "inventory_count_id", nullable = false)
    private UUID inventoryCountId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "expected_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal expectedQty;

    @Column(name = "counted_qty", precision = 15, scale = 3)
    private BigDecimal countedQty;

    @Column(name = "second_counted_qty", precision = 15, scale = 3)
    private BigDecimal secondCountedQty;

    @Column(name = "adjusted_qty", precision = 15, scale = 3)
    private BigDecimal adjustedQty;

    @Column(name = "divergence_qty", precision = 15, scale = 3)
    private BigDecimal divergenceQty;

    @Column(name = "divergence_pct", precision = 8, scale = 4)
    private BigDecimal divergencePct;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ItemCountStatus status;

    // Lazy-loaded associations for response mapping
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", insertable = false, updatable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private ProductWms product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", insertable = false, updatable = false)
    private Lot lot;
}
