package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseEntity;
import com.odin.wms.domain.enums.LocationType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "locations")
public class Location extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shelf_id", nullable = false)
    private Shelf shelf;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "full_address", nullable = false)
    private String fullAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    @Builder.Default
    private LocationType type = LocationType.STORAGE;

    @Column(name = "capacity_units")
    private Integer capacityUnits;

    @Column(name = "capacity_weight_kg", precision = 10, scale = 3)
    private BigDecimal capacityWeightKg;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
