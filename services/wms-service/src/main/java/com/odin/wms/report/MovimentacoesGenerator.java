package com.odin.wms.report;

import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.repository.StockMovementRepository;
import com.odin.wms.dto.report.MovimentacaoRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Gera linhas para o relatório de Movimentações por Período.
 */
@Component
@RequiredArgsConstructor
public class MovimentacoesGenerator {

    private final StockMovementRepository stockMovementRepository;

    public Page<MovimentacaoRow> generate(
            UUID warehouseId,
            LocalDate dataInicio,
            LocalDate dataFim,
            UUID productId,
            MovementType movementType,
            UUID locationId,
            UUID tenantId,
            Pageable pageable
    ) {
        Instant from = dataInicio.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = dataFim.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return stockMovementRepository
                .findMovimentacoes(tenantId, warehouseId, from, to, productId, movementType, locationId, pageable)
                .map(this::toRow);
    }

    private MovimentacaoRow toRow(StockMovement m) {
        UUID lotId      = m.getLot() != null ? m.getLot().getId() : null;
        UUID locationId = m.getSourceLocation() != null
                ? m.getSourceLocation().getId()
                : (m.getDestinationLocation() != null ? m.getDestinationLocation().getId() : null);

        return new MovimentacaoRow(
                m.getId(),
                m.getType(),
                m.getReferenceType(),
                m.getReferenceId(),
                m.getProduct().getId(),
                m.getProduct().getSku(),
                lotId,
                locationId,
                m.getQuantity(),
                m.getOperatorId(),
                m.getCreatedAt()
        );
    }
}
