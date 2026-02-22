package com.odin.wms.domain.repository;

import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.enums.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
}
