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
}
