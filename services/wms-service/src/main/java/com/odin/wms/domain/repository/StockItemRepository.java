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

    // -------------------------------------------------------------------------
    // Story 4.1 — Controle de Estoque em Tempo Real
    // -------------------------------------------------------------------------

    /**
     * Filtro flexível para GET /stock/balance.
     * Parâmetros opcionais: null = sem filtro para aquela dimensão.
     * Faz JOIN FETCH de product, location e lot para evitar N+1.
     */
    @Query("""
            SELECT DISTINCT s FROM StockItem s
            JOIN FETCH s.product p
            JOIN FETCH s.location l
            LEFT JOIN FETCH s.lot lot
            WHERE s.tenantId = :tenantId
            AND (:productId IS NULL OR p.id = :productId)
            AND (:locationId IS NULL OR l.id = :locationId)
            AND (:lotId IS NULL OR (lot IS NOT NULL AND lot.id = :lotId))
            AND (:warehouseId IS NULL OR l.shelf.aisle.zone.warehouse.id = :warehouseId)
            """)
    List<StockItem> findByFilters(@Param("tenantId") UUID tenantId,
                                  @Param("productId") UUID productId,
                                  @Param("locationId") UUID locationId,
                                  @Param("lotId") UUID lotId,
                                  @Param("warehouseId") UUID warehouseId);

    /**
     * Count de localizações ocupadas por zona (quantidade > 0 em qualquer status).
     * Retorna List<Object[]> onde [0]=zoneId (UUID), [1]=count (Long).
     * Usado em StockBalanceService.getOccupation().
     */
    @Query("""
            SELECT s.location.shelf.aisle.zone.id, COUNT(DISTINCT s.location.id)
            FROM StockItem s
            WHERE s.tenantId = :tenantId
            AND s.location.shelf.aisle.zone.warehouse.id = :warehouseId
            AND (s.quantityAvailable > 0 OR s.quantityReserved > 0
                 OR s.quantityQuarantine > 0 OR s.quantityDamaged > 0)
            GROUP BY s.location.shelf.aisle.zone.id
            """)
    List<Object[]> countOccupiedLocationsByZone(@Param("tenantId") UUID tenantId,
                                                @Param("warehouseId") UUID warehouseId);

    /**
     * Soma total de unidades em estoque por zona (available + reserved + quarantine + damaged).
     * Retorna List<Object[]> onde [0]=zoneId (UUID), [1]=sum (Long).
     * Usado para calcular usedCapacityUnits em StockBalanceService.getOccupation().
     */
    @Query("""
            SELECT s.location.shelf.aisle.zone.id,
                   COALESCE(SUM(s.quantityAvailable + s.quantityReserved
                                + s.quantityQuarantine + s.quantityDamaged), 0)
            FROM StockItem s
            WHERE s.tenantId = :tenantId
            AND s.location.shelf.aisle.zone.warehouse.id = :warehouseId
            GROUP BY s.location.shelf.aisle.zone.id
            """)
    List<Object[]> sumQuantitiesByZone(@Param("tenantId") UUID tenantId,
                                       @Param("warehouseId") UUID warehouseId);

    // -------------------------------------------------------------------------
    // Story 4.2 — Rastreabilidade / FEFO Expiry
    // -------------------------------------------------------------------------

    /**
     * Busca em lote StockItems com quantidade disponível para múltiplos lotIds.
     * JOIN FETCH da hierarquia de localização para evitar N+1 em ExpiryResponse.
     * Usado em LotTraceabilityService.getExpiryByProduct().
     */
    @Query("""
            SELECT s FROM StockItem s
            JOIN FETCH s.location l
            JOIN FETCH l.shelf sh
            JOIN FETCH sh.aisle ai
            JOIN FETCH ai.zone z
            JOIN FETCH z.warehouse w
            WHERE s.tenantId = :tenantId
              AND s.lot.id IN :lotIds
              AND s.quantityAvailable > 0
            """)
    List<StockItem> findAvailableByTenantIdAndLotIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("lotIds") List<UUID> lotIds);
}
