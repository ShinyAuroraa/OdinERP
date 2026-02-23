package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.Location;
import com.odin.wms.domain.enums.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    Optional<Location> findByTenantIdAndCode(UUID tenantId, String code);

    List<Location> findByTenantIdAndType(UUID tenantId, LocationType type);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<Location> findByTenantIdAndShelfId(UUID tenantId, UUID shelfId);

    /**
     * Busca uma RECEIVING_DOCK location validando que ela pertence ao warehouse e tenant informados.
     * Necessário para validação em ReceivingNoteService.create().
     */
    @Query("""
            SELECT l FROM Location l
            JOIN l.shelf s
            JOIN s.aisle a
            JOIN a.zone z
            WHERE l.id = :locationId
              AND l.tenantId = :tenantId
              AND l.type = com.odin.wms.domain.enums.LocationType.RECEIVING_DOCK
              AND z.warehouse.id = :warehouseId
            """)
    Optional<Location> findReceivingDockByIdAndWarehouse(
            @Param("locationId") UUID locationId,
            @Param("warehouseId") UUID warehouseId,
            @Param("tenantId") UUID tenantId);

    /**
     * Lista RECEIVING_DOCK locations de um warehouse para o tenant.
     * Usado pelo Kafka consumer para escolher a dock padrão.
     */
    @Query("""
            SELECT l FROM Location l
            JOIN l.shelf s
            JOIN s.aisle a
            JOIN a.zone z
            WHERE l.tenantId = :tenantId
              AND l.type = com.odin.wms.domain.enums.LocationType.RECEIVING_DOCK
              AND z.warehouse.id = :warehouseId
              AND l.active = true
            ORDER BY l.createdAt ASC
            """)
    List<Location> findReceivingDocksByWarehouse(
            @Param("warehouseId") UUID warehouseId,
            @Param("tenantId") UUID tenantId);

    /**
     * Lista localizações de armazenagem (STORAGE ou PICKING) de um warehouse.
     * Usado pelo motor FIFO/FEFO em PutawayService.suggestLocation().
     * Ordenado por fullAddress para resultado determinístico.
     */
    @Query("""
            SELECT l FROM Location l
            JOIN l.shelf s
            JOIN s.aisle a
            JOIN a.zone z
            WHERE l.tenantId = :tenantId
              AND l.type IN (
                  com.odin.wms.domain.enums.LocationType.STORAGE,
                  com.odin.wms.domain.enums.LocationType.PICKING
              )
              AND l.active = true
              AND z.warehouse.id = :warehouseId
            ORDER BY l.fullAddress ASC
            """)
    List<Location> findStorageOrPickingByWarehouse(
            @Param("warehouseId") UUID warehouseId,
            @Param("tenantId") UUID tenantId);

    /**
     * Retorna o ID do warehouse ao qual uma location pertence.
     * Evita lazy-load da cadeia Location → Shelf → Aisle → Zone → Warehouse.
     * Usado em PutawayService.confirm() para validação cross-warehouse.
     */
    @Query("""
            SELECT z.warehouse.id FROM Location l
            JOIN l.shelf s
            JOIN s.aisle a
            JOIN a.zone z
            WHERE l.id = :locationId
            """)
    Optional<UUID> findWarehouseIdByLocationId(@Param("locationId") UUID locationId);

    /**
     * Lista localizações de quarentena (QUARANTINE) de um warehouse.
     * Usado em QuarantineService.generateTasks() para encontrar a zona de quarentena.
     * Ordenado por fullAddress para resultado determinístico.
     */
    @Query("""
            SELECT l FROM Location l
            JOIN l.shelf s
            JOIN s.aisle a
            JOIN a.zone z
            WHERE l.tenantId = :tenantId
              AND l.type = com.odin.wms.domain.enums.LocationType.QUARANTINE
              AND l.active = true
              AND z.warehouse.id = :warehouseId
            ORDER BY l.fullAddress ASC
            """)
    List<Location> findQuarantineByWarehouse(
            @Param("warehouseId") UUID warehouseId,
            @Param("tenantId") UUID tenantId);

    // -------------------------------------------------------------------------
    // Story 4.1 — Controle de Estoque em Tempo Real
    // -------------------------------------------------------------------------

    /**
     * Contagem de localizações por zona (Spring Data derived query — Task 6.1).
     * Navega shelf.aisle.zone.id de forma implícita pelo Spring Data JPA.
     */
    long countByTenantIdAndShelfAisleZoneId(UUID tenantId, UUID zoneId);

    /**
     * Contagem de localizações por zona em lote (para um warehouse inteiro).
     * Evita N+1: uma única query retorna contagens para todas as zonas do warehouse.
     * Retorna List<Object[]> onde [0]=zoneId (UUID), [1]=count (Long).
     */
    @Query("""
            SELECT l.shelf.aisle.zone.id, COUNT(l)
            FROM Location l
            JOIN l.shelf s JOIN s.aisle a JOIN a.zone z
            WHERE l.tenantId = :tenantId AND z.warehouse.id = :warehouseId
            GROUP BY l.shelf.aisle.zone.id
            """)
    List<Object[]> countLocationsByZone(@Param("tenantId") UUID tenantId,
                                        @Param("warehouseId") UUID warehouseId);

    /**
     * Soma da capacidade (capacityUnits) por zona em lote.
     * Retorna List<Object[]> onde [0]=zoneId (UUID), [1]=sumCapacity (Long, COALESCE → 0 se null).
     */
    @Query("""
            SELECT l.shelf.aisle.zone.id, COALESCE(SUM(l.capacityUnits), 0)
            FROM Location l
            JOIN l.shelf s JOIN s.aisle a JOIN a.zone z
            WHERE l.tenantId = :tenantId AND z.warehouse.id = :warehouseId
            GROUP BY l.shelf.aisle.zone.id
            """)
    List<Object[]> sumCapacityUnitsByZone(@Param("tenantId") UUID tenantId,
                                          @Param("warehouseId") UUID warehouseId);
}
