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

    /**
     * Retorna contagem de StockItems por localização para um tenant.
     * Evita N+1 queries ao verificar capacidade de múltiplas localizações.
     * Resultado: List<Object[]> onde [0]=locationId (UUID), [1]=count (Long).
     */
    @Query("""
            SELECT s.location.id, COUNT(s) FROM StockItem s
            WHERE s.tenantId = :tenantId
            GROUP BY s.location.id
            """)
    List<Object[]> countByTenantIdGroupByLocationId(@Param("tenantId") UUID tenantId);

    /**
     * FEFO — retorna IDs de localizações que já contêm o produto,
     * ordenados pelo menor expiryDate do lot (NULL por último).
     * Preferir co-localização com lote mais próximo do vencimento.
     */
    @Query("""
            SELECT s.location.id FROM StockItem s
            WHERE s.tenantId = :tenantId
              AND s.product.id = :productId
            GROUP BY s.location.id
            ORDER BY MIN(s.lot.expiryDate) ASC NULLS LAST
            """)
    List<UUID> findLocationIdsWithProductByExpiryFEFO(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId);

    /**
     * FIFO — retorna IDs distintos de localizações que já contêm o produto.
     * Preferir co-localização para agrupamento de SKU.
     */
    @Query("""
            SELECT DISTINCT s.location.id FROM StockItem s
            WHERE s.tenantId = :tenantId
              AND s.product.id = :productId
            """)
    List<UUID> findLocationIdsWithProduct(
            @Param("tenantId") UUID tenantId,
            @Param("productId") UUID productId);

    /**
     * Contagem de itens em uma localização específica.
     * Usado em PutawayService.confirm() para validar capacidade antes de confirmar.
     */
    long countByTenantIdAndLocationId(UUID tenantId, UUID locationId);
}
