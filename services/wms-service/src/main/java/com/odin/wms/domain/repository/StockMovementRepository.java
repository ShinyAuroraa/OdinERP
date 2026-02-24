package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /** Idempotência: verifica se evento Kafka já foi processado. */
    boolean existsByKafkaEventId(String kafkaEventId);

    Page<StockMovement> findByTenantIdAndProductId(UUID tenantId, UUID productId, Pageable pageable);

    Page<StockMovement> findByTenantIdAndTypeAndCreatedAtBetween(
            UUID tenantId, MovementType type, Instant from, Instant to, Pageable pageable);

    Optional<StockMovement> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId);

    // -------------------------------------------------------------------------
    // Story 7.1 — Relatórios Regulatórios
    // -------------------------------------------------------------------------

    /**
     * Busca em lote movimentos de múltiplos lotes num período.
     * Substitui N*M queries individuais por 1 query com IN clause.
     * Usado em AnvisaReportGenerator para eliminar padrão N+1 (QA-7.1-TD-002).
     */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.product p
            LEFT JOIN FETCH m.lot l
            WHERE m.tenantId = :tenantId
              AND m.lot.id IN :lotIds
              AND m.createdAt >= :from
              AND m.createdAt < :to
            ORDER BY m.createdAt ASC
            """)
    List<StockMovement> findByTenantIdAndLotIdInAndCreatedAtBetween(
            @Param("tenantId") UUID tenantId,
            @Param("lotIds") List<UUID> lotIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Busca movimentos de um warehouse no período (join com hierarquia de localização).
     * Inclui movimentos onde a localização de origem OU destino pertence ao warehouse.
     */
    @Query("""
            SELECT m FROM StockMovement m
            JOIN FETCH m.product p
            LEFT JOIN FETCH m.lot
            LEFT JOIN FETCH m.sourceLocation sl
            LEFT JOIN FETCH m.destinationLocation dl
            WHERE m.tenantId = :tenantId
              AND m.createdAt BETWEEN :from AND :to
              AND (
                (sl IS NOT NULL AND sl.shelf.aisle.zone.warehouse.id = :warehouseId)
                OR (dl IS NOT NULL AND dl.shelf.aisle.zone.warehouse.id = :warehouseId)
              )
            ORDER BY m.createdAt ASC
            """)
    List<StockMovement> findByTenantIdAndWarehouseIdAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Busca movimentos com filtros opcionais para o relatório de Movimentações.
     */
    @Query("""
            SELECT m FROM StockMovement m
            JOIN FETCH m.product p
            LEFT JOIN FETCH m.lot
            LEFT JOIN FETCH m.sourceLocation sl
            LEFT JOIN FETCH m.destinationLocation dl
            WHERE m.tenantId = :tenantId
              AND m.createdAt BETWEEN :from AND :to
              AND (:productId IS NULL OR p.id = :productId)
              AND (:movementType IS NULL OR m.type = :movementType)
              AND (:locationId IS NULL OR
                   (sl IS NOT NULL AND sl.id = :locationId) OR
                   (dl IS NOT NULL AND dl.id = :locationId))
              AND (
                (sl IS NOT NULL AND sl.shelf.aisle.zone.warehouse.id = :warehouseId)
                OR (dl IS NOT NULL AND dl.shelf.aisle.zone.warehouse.id = :warehouseId)
              )
            ORDER BY m.createdAt DESC
            """)
    Page<StockMovement> findMovimentacoes(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("productId") UUID productId,
            @Param("movementType") MovementType movementType,
            @Param("locationId") UUID locationId,
            Pageable pageable);

    // -------------------------------------------------------------------------
    // Story 4.2 — Rastreabilidade
    // -------------------------------------------------------------------------

    /**
     * Histórico completo de movimentos de um lote, ordenado cronologicamente.
     * JOIN FETCH para evitar N+1 em product, sourceLocation, destinationLocation.
     */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.sourceLocation
            LEFT JOIN FETCH m.destinationLocation
            WHERE m.tenantId = :tenantId
              AND m.lot.id = :lotId
            ORDER BY m.createdAt ASC
            """)
    List<StockMovement> findByTenantIdAndLotIdOrderByCreatedAtAsc(
            @Param("tenantId") UUID tenantId,
            @Param("lotId") UUID lotId);

    /**
     * Histórico completo de movimentos de um número de série, ordenado cronologicamente.
     */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.sourceLocation
            LEFT JOIN FETCH m.destinationLocation
            WHERE m.tenantId = :tenantId
              AND m.serialNumber.id = :serialNumberId
            ORDER BY m.createdAt ASC
            """)
    List<StockMovement> findByTenantIdAndSerialNumberIdOrderByCreatedAtAsc(
            @Param("tenantId") UUID tenantId,
            @Param("serialNumberId") UUID serialNumberId);

    /**
     * Árvore de rastreabilidade — movimentos de um lote com paginação para limite configurável.
     * Usado em GET /traceability/lot/{lotId}/tree com wms.traceability.max-movements.
     */
    @Query("""
            SELECT m FROM StockMovement m
            LEFT JOIN FETCH m.sourceLocation
            LEFT JOIN FETCH m.destinationLocation
            WHERE m.tenantId = :tenantId
              AND m.lot.id = :lotId
            ORDER BY m.createdAt ASC
            """)
    List<StockMovement> findTraceabilityTree(
            @Param("tenantId") UUID tenantId,
            @Param("lotId") UUID lotId,
            Pageable pageable);
}
