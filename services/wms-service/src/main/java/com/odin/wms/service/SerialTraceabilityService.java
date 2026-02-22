package com.odin.wms.service;

import com.odin.wms.domain.entity.SerialNumber;
import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.repository.SerialNumberRepository;
import com.odin.wms.domain.repository.StockMovementRepository;
import com.odin.wms.dto.response.LotTraceabilityResponse.MovementItemResponse;
import com.odin.wms.dto.response.SerialTraceabilityResponse;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityDocument;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service de rastreabilidade por número de série.
 * Estratégia: Elasticsearch first, fallback gracioso para PostgreSQL.
 * AC2, AC7, AC8.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SerialTraceabilityService {

    private final SerialNumberRepository serialNumberRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TraceabilityRepository traceabilityRepository;

    // -------------------------------------------------------------------------
    // AC2 — GET /traceability/serial/{serialNumber}
    // -------------------------------------------------------------------------

    /**
     * Retorna histórico completo de movimentos de um número de série.
     * ES first → fallback PostgreSQL se ES indisponível.
     * Lança ResourceNotFoundException (404) se série não pertence ao tenant.
     */
    public SerialTraceabilityResponse getSerialHistory(UUID tenantId, String serialNumber) {
        SerialNumber serial = serialNumberRepository.findByTenantIdAndSerialNumber(tenantId, serialNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Número de série não encontrado: " + serialNumber));

        List<MovementItemResponse> movements = fetchMovementsFromEsOrDb(tenantId, serial);

        return new SerialTraceabilityResponse(
                serial.getId(),
                serial.getSerialNumber(),
                serial.getProduct().getId(),
                serial.getProduct().getSku(),
                serial.getLocation() != null ? serial.getLocation().getCode() : null,
                serial.getStatus().name(),
                movements
        );
    }

    // -------------------------------------------------------------------------
    // Private — ES first, PostgreSQL fallback
    // -------------------------------------------------------------------------

    private List<MovementItemResponse> fetchMovementsFromEsOrDb(UUID tenantId, SerialNumber serial) {
        try {
            List<TraceabilityDocument> docs = traceabilityRepository
                    .findByTenantIdAndSerialNumberOrderByCreatedAtAsc(
                            tenantId.toString(), serial.getSerialNumber());
            if (!docs.isEmpty()) {
                return docs.stream().map(this::toMovementItemResponseFromDoc).toList();
            }
            log.debug("ES sem resultados para serial {}, usando PostgreSQL", serial.getSerialNumber());
        } catch (Exception e) {
            log.warn("ES indisponível, usando PostgreSQL para serial {}: {}",
                    serial.getSerialNumber(), e.getMessage());
        }
        return stockMovementRepository
                .findByTenantIdAndSerialNumberIdOrderByCreatedAtAsc(tenantId, serial.getId())
                .stream()
                .map(this::toMovementItemResponseFromEntity)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private — mappers
    // -------------------------------------------------------------------------

    private MovementItemResponse toMovementItemResponseFromEntity(StockMovement m) {
        return new MovementItemResponse(
                m.getId(),
                m.getType().name(),
                m.getQuantity(),
                m.getSourceLocation() != null ? m.getSourceLocation().getId() : null,
                m.getSourceLocation() != null ? m.getSourceLocation().getCode() : null,
                m.getDestinationLocation() != null ? m.getDestinationLocation().getId() : null,
                m.getDestinationLocation() != null ? m.getDestinationLocation().getCode() : null,
                m.getOperatorId() != null ? m.getOperatorId().toString() : null,
                m.getReferenceId(),
                m.getReferenceType() != null ? m.getReferenceType().name() : null,
                m.getCreatedAt(),
                m.getReason()
        );
    }

    private MovementItemResponse toMovementItemResponseFromDoc(TraceabilityDocument doc) {
        return new MovementItemResponse(
                UUID.fromString(doc.getId()),
                doc.getMovementType(),
                doc.getQuantity(),
                null,
                doc.getSourceLocationCode(),
                null,
                doc.getDestinationLocationCode(),
                doc.getUserId(),
                doc.getReferenceId() != null ? UUID.fromString(doc.getReferenceId()) : null,
                doc.getReferenceType(),
                doc.getCreatedAt(),
                null
        );
    }
}
