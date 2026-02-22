package com.odin.wms.domain.entity;

import com.odin.wms.domain.entity.base.BaseAppendOnlyEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lote de produto. Imutável por design operacional — apenas createdAt, sem updatedAt.
 * expiry_date é a chave para o algoritmo FEFO (First Expired, First Out).
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "lots")
public class Lot extends BaseAppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductWms product;

    @Column(name = "lot_number", nullable = false, length = 100)
    private String lotNumber;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    /** Chave para FEFO — First Expired, First Out. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** UUID do fornecedor — referência cross-service sem FK. */
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "supplier_lot_number", length = 100)
    private String supplierLotNumber;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
