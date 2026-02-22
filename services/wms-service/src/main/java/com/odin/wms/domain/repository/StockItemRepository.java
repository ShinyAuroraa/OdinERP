package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.StockItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    Optional<StockItem> findByTenantIdAndLocationIdAndProductIdAndLotId(
            UUID tenantId, UUID locationId, UUID productId, UUID lotId);

    Optional<StockItem> findByTenantIdAndLocationIdAndProductIdAndLotIdIsNull(
            UUID tenantId, UUID locationId, UUID productId);

    /**
     * FIFO — First In, First Out.
     * Retorna saldos com estoque disponível, ordenados por receivedAt ASC.
     */
    @Query("""
            SELECT s FROM StockItem s
            WHERE s.tenantId = :tenantId
              AND s.product.id = :productId
              AND s.quantityAvailable > 0
            ORDER BY s.receivedAt ASC
            """)
    List<StockItem> findAvailableStockFIFO(@Param("tenantId") UUID tenantId,
                                           @Param("productId") UUID productId);

    /**
     * Locking pessimista para reserva de estoque em operações concorrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id = :id")
    Optional<StockItem> findByIdWithLock(@Param("id") UUID id);
}
