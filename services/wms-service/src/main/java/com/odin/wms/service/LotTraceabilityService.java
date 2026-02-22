package com.odin.wms.service;

import com.odin.wms.domain.entity.Lot;
import com.odin.wms.domain.entity.StockItem;
import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.repository.LotRepository;
import com.odin.wms.domain.repository.StockItemRepository;
import com.odin.wms.domain.repository.StockMovementRepository;
import com.odin.wms.dto.response.ExpiryResponse;
import com.odin.wms.dto.response.LotTraceabilityResponse;
import com.odin.wms.dto.response.LotTraceabilityResponse.MovementItemResponse;
import com.odin.wms.dto.response.TraceabilityTreeResponse;
import com.odin.wms.dto.response.TraceabilityTreeResponse.TraceabilityEvent;
import com.odin.wms.exception.ResourceNotFoundException;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityDocument;
import com.odin.wms.infrastructure.elasticsearch.TraceabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service de rastreabilidade por lote.
 * Estratégia: Elasticsearch first, fallback gracioso para PostgreSQL.
 * AC1, AC3, AC4, AC7, AC8.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LotTraceabilityService {

    private final LotRepository lotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockItemRepository stockItemRepository;
    private final TraceabilityRepository traceabilityRepository;

    @Value("${wms.traceability.max-movements:500}")
    private int maxMovements;

    // -------------------------------------------------------------------------
    // AC1 — GET /traceability/lot/{lotNumber}
    // -------------------------------------------------------------------------

    /**
     * Retorna histórico completo de movimentos de um lote.
     * ES first → fallback PostgreSQL se ES indisponível ou sem resultados.
     * Lança ResourceNotFoundException (404) se lote não pertence ao tenant.
     */
    public LotTraceabilityResponse getLotHistory(UUID tenantId, String lotNumber) {
        Lot lot = lotRepository.findByTenantIdAndLotNumber(tenantId, lotNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lote não encontrado: " + lotNumber));

        List<MovementItemResponse> movements = fetchMovementsFromEsOrDb(tenantId, lot);
        return buildLotResponse(lot, movements);
    }

    // -------------------------------------------------------------------------
    // AC3 — GET /traceability/lot/{lotId}/tree
    // -------------------------------------------------------------------------

    /**
     * Retorna árvore de rastreabilidade completa do lote.
     * Limitada a wms.traceability.max-movements (padrão: 500).
     */
    public TraceabilityTreeResponse getTraceabilityTree(UUID tenantId, UUID lotId) {
        Lot lot = lotRepository.findById(lotId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lote não encontrado: " + lotId));

        List<StockMovement> movements = stockMovementRepository.findTraceabilityTree(
                tenantId, lotId, PageRequest.of(0, maxMovements));

        // Quantidade remanescente via StockItems
        int remaining = stockItemRepository
                .findAvailableByTenantIdAndLotIdIn(tenantId, List.of(lotId))
                .stream()
                .mapToInt(StockItem::getQuantityAvailable)
                .sum();

        return buildTree(lot, movements, remaining);
    }

    // -------------------------------------------------------------------------
    // AC4 — GET /traceability/product/{productId}/expiry
    // -------------------------------------------------------------------------

    /**
     * Retorna lotes por validade (FEFO) com filtros opcionais.
     * Apenas lotes com quantityAvailable > 0.
     */
    public List<ExpiryResponse> getExpiryByProduct(UUID tenantId, UUID productId,
                                                    UUID warehouseId, LocalDate expiryBefore) {
        List<Lot> lots = lotRepository.findAvailableByProductFefo(tenantId, productId, expiryBefore);
        if (lots.isEmpty()) return List.of();

        List<UUID> lotIds = lots.stream().map(Lot::getId).toList();
        List<StockItem> items = stockItemRepository.findAvailableByTenantIdAndLotIdIn(tenantId, lotIds);

        // Agrupa StockItems por lotId
        Map<UUID, List<StockItem>> byLot = items.stream()
                .collect(Collectors.groupingBy(si -> si.getLot().getId()));

        List<ExpiryResponse> result = new ArrayList<>();
        for (Lot lot : lots) {
            List<StockItem> lotItems = byLot.getOrDefault(lot.getId(), List.of());

            if (warehouseId != null) {
                lotItems = lotItems.stream()
                        .filter(si -> warehouseId.equals(
                                si.getLocation().getShelf().getAisle().getZone().getWarehouse().getId()))
                        .toList();
            }
            if (lotItems.isEmpty()) continue;

            int totalQty = lotItems.stream().mapToInt(StockItem::getQuantityAvailable).sum();
            if (totalQty <= 0) continue;

            StockItem first = lotItems.get(0);
            UUID whId = first.getLocation().getShelf().getAisle().getZone().getWarehouse().getId();

            Integer daysUntil = lot.getExpiryDate() != null
                    ? (int) ChronoUnit.DAYS.between(LocalDate.now(), lot.getExpiryDate())
                    : null;

            result.add(new ExpiryResponse(
                    lot.getId(),
                    lot.getLotNumber(),
                    lot.getExpiryDate(),
                    daysUntil,
                    totalQty,
                    first.getLocation().getCode(),
                    whId
            ));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private — ES first, PostgreSQL fallback
    // -------------------------------------------------------------------------

    private List<MovementItemResponse> fetchMovementsFromEsOrDb(UUID tenantId, Lot lot) {
        try {
            List<TraceabilityDocument> docs = traceabilityRepository
                    .findByTenantIdAndLotNumberOrderByCreatedAtAsc(
                            tenantId.toString(), lot.getLotNumber());
            if (!docs.isEmpty()) {
                return docs.stream().map(this::toMovementItemResponseFromDoc).toList();
            }
            log.debug("ES sem resultados para lote {}, usando PostgreSQL", lot.getLotNumber());
        } catch (Exception e) {
            log.warn("ES indisponível, usando PostgreSQL para lote {}: {}", lot.getLotNumber(), e.getMessage());
        }
        return stockMovementRepository
                .findByTenantIdAndLotIdOrderByCreatedAtAsc(tenantId, lot.getId())
                .stream()
                .map(this::toMovementItemResponseFromEntity)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private — mappers
    // -------------------------------------------------------------------------

    private LotTraceabilityResponse buildLotResponse(Lot lot, List<MovementItemResponse> movements) {
        return new LotTraceabilityResponse(
                lot.getId(),
                lot.getLotNumber(),
                lot.getProduct().getId(),
                lot.getProduct().getSku(),
                lot.getExpiryDate(),
                movements,
                movements.size()
        );
    }

    private TraceabilityTreeResponse buildTree(Lot lot, List<StockMovement> movements, int remaining) {
        AtomicInteger seq = new AtomicInteger(1);
        List<TraceabilityEvent> events = movements.stream()
                .map(m -> new TraceabilityEvent(
                        seq.getAndIncrement(),
                        m.getType().name(),
                        m.getCreatedAt(),
                        m.getDestinationLocation() != null ? m.getDestinationLocation().getCode()
                                : (m.getSourceLocation() != null ? m.getSourceLocation().getCode() : null),
                        m.getQuantity(),
                        m.getOperatorId() != null ? m.getOperatorId().toString() : null
                ))
                .toList();

        // Determina data de recebimento (primeiro movimento INBOUND)
        Instant receivedAt = movements.stream()
                .filter(m -> m.getType() == MovementType.INBOUND)
                .map(StockMovement::getCreatedAt)
                .findFirst()
                .orElse(lot.getCreatedAt());

        String currentStatus = computeCurrentStatus(lot, movements, remaining);

        return new TraceabilityTreeResponse(
                lot.getId(),
                lot.getLotNumber(),
                lot.getProduct().getSku(),
                lot.getExpiryDate(),
                lot.getSupplierId() != null ? lot.getSupplierId().toString() : null,
                receivedAt,
                events,
                currentStatus,
                remaining
        );
    }

    private String computeCurrentStatus(Lot lot, List<StockMovement> movements, int remaining) {
        if (!Boolean.TRUE.equals(lot.getActive())) return "INACTIVE";
        int totalIn  = movements.stream().filter(m -> m.getType() == MovementType.INBOUND)
                .mapToInt(StockMovement::getQuantity).sum();
        int totalOut = movements.stream().filter(m -> m.getType() == MovementType.SHIPPING)
                .mapToInt(StockMovement::getQuantity).sum();
        if (remaining <= 0 && totalOut >= totalIn && totalIn > 0) return "CONSUMED";
        if (totalOut > 0) return "PARTIALLY_CONSUMED";
        return "IN_STOCK";
    }

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
                null,   // locationId não disponível no ES
                doc.getSourceLocationCode(),
                null,
                doc.getDestinationLocationCode(),
                doc.getUserId(),
                doc.getReferenceId() != null ? UUID.fromString(doc.getReferenceId()) : null,
                doc.getReferenceType(),
                doc.getCreatedAt(),
                null    // notes/reason não indexado no ES
        );
    }
}
