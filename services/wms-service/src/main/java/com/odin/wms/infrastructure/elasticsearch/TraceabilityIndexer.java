package com.odin.wms.infrastructure.elasticsearch;

import com.odin.wms.domain.entity.StockMovement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Indexador assíncrono de StockMovements no Elasticsearch.
 * O método nunca propaga exceções — falha do ES é não-crítica.
 *
 * AC7 — @Async garante que operações de escrita no PostgreSQL não bloqueiam.
 * Requer @EnableAsync em AsyncConfig.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceabilityIndexer {

    private final TraceabilityRepository traceabilityRepository;

    /**
     * Indexa um StockMovement no Elasticsearch de forma assíncrona.
     * Nunca lança exceção — degradação graciosa.
     */
    @Async
    public void indexMovementAsync(StockMovement movement) {
        try {
            TraceabilityDocument doc = toDocument(movement);
            traceabilityRepository.save(doc);
            log.debug("Movimento {} indexado no ES (tenant={})", movement.getId(), movement.getTenantId());
        } catch (Exception e) {
            log.warn("Falha ao indexar movimento {} no ES (não-crítico): {}", movement.getId(), e.getMessage());
            // Nunca propaga — ES failure é não-crítica (fallback para PostgreSQL)
        }
    }

    private TraceabilityDocument toDocument(StockMovement movement) {
        TraceabilityDocument doc = new TraceabilityDocument();
        doc.setId(movement.getId().toString());
        doc.setTenantId(movement.getTenantId().toString());
        doc.setProductId(movement.getProduct().getId().toString());
        doc.setMovementType(movement.getType().name());
        doc.setQuantity(movement.getQuantity());
        doc.setCreatedAt(movement.getCreatedAt());

        if (movement.getLot() != null) {
            doc.setLotId(movement.getLot().getId().toString());
            doc.setLotNumber(movement.getLot().getLotNumber());
        }
        if (movement.getSerialNumber() != null) {
            doc.setSerialNumber(movement.getSerialNumber().getSerialNumber());
        }
        if (movement.getSourceLocation() != null) {
            doc.setSourceLocationCode(movement.getSourceLocation().getCode());
        }
        if (movement.getDestinationLocation() != null) {
            doc.setDestinationLocationCode(movement.getDestinationLocation().getCode());
        }
        if (movement.getOperatorId() != null) {
            doc.setUserId(movement.getOperatorId().toString());
        }
        if (movement.getReferenceId() != null) {
            doc.setReferenceId(movement.getReferenceId().toString());
        }
        if (movement.getReferenceType() != null) {
            doc.setReferenceType(movement.getReferenceType().name());
        }
        return doc;
    }
}
