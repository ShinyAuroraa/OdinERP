package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import com.odin.wms.domain.enums.StorageType;
import jakarta.persistence.*;
import lombok.Builder;
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
@Table(name = "products_wms")
public class ProductWms extends BaseEntity {

    /** Referência ao cadastro master de produtos (sem FK cross-service). */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ean13", length = 13)
    private String ean13;

    @Column(name = "gs1_128")
    private String gs1128;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 50)
    @Builder.Default
    private StorageType storageType = StorageType.DRY;

    @Column(name = "controls_lot", nullable = false)
    @Builder.Default
    private Boolean controlsLot = false;

    @Column(name = "controls_serial", nullable = false)
    @Builder.Default
    private Boolean controlsSerial = false;

    @Column(name = "controls_expiry", nullable = false)
    @Builder.Default
    private Boolean controlsExpiry = false;

    @Column(name = "unit_width_cm", precision = 10, scale = 2)
    private BigDecimal unitWidthCm;

    @Column(name = "unit_height_cm", precision = 10, scale = 2)
    private BigDecimal unitHeightCm;

    @Column(name = "unit_depth_cm", precision = 10, scale = 2)
    private BigDecimal unitDepthCm;

    @Column(name = "unit_weight_kg", precision = 10, scale = 3)
    private BigDecimal unitWeightKg;

    @Column(name = "units_per_location")
    private Integer unitsPerLocation;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Story 7.1 — flag de controle pela ANVISA Vigilância Sanitária. */
    @Column(name = "vigilancia_sanitaria", nullable = false)
    @Builder.Default
    private boolean vigilanciaSanitaria = false;
}
