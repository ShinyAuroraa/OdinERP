package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LotRepository extends JpaRepository<Lot, UUID> {

    Optional<Lot> findByTenantIdAndProductIdAndLotNumber(UUID tenantId, UUID productId, String lotNumber);

    /**
     * FEFO — First Expired, First Out.
     * Retorna lotes ativos ordenados por data de expiração (nulos por último).
     */
    @Query("""
            SELECT l FROM Lot l
            WHERE l.tenantId = :tenantId
              AND l.product.id = :productId
              AND l.active = true
            ORDER BY l.expiryDate ASC NULLS LAST, l.createdAt ASC
            """)
    List<Lot> findActiveLotsFEFO(@Param("tenantId") UUID tenantId,
                                 @Param("productId") UUID productId);

    boolean existsByTenantIdAndProductIdAndLotNumber(UUID tenantId, UUID productId, String lotNumber);
}
