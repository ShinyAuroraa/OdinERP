package com.odin.wms.report;

import com.odin.wms.domain.entity.Lot;
import com.odin.wms.domain.entity.StockItem;
import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;
import com.odin.wms.domain.repository.LotRepository;
import com.odin.wms.domain.repository.StockItemRepository;
import com.odin.wms.domain.repository.StockMovementRepository;
import com.odin.wms.dto.report.RastreabilidadeLoteResponse;
import com.odin.wms.dto.report.RastreabilidadeLoteResponse.*;
import com.odin.wms.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gera a árvore completa de rastreabilidade de um lote para recall.
 */
@Component
@RequiredArgsConstructor
public class RastreabilidadeGenerator {

    private final LotRepository lotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockItemRepository stockItemRepository;

    public RastreabilidadeLoteResponse generate(UUID lotId, UUID tenantId) {
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote não encontrado: " + lotId));

        if (!lot.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Lote pertence a outro tenant");
        }

        // Info do lote
        LoteInfo loteInfo = new LoteInfo(
                lot.getId(),
                lot.getLotNumber(),
                lot.getProduct().getId(),
                lot.getProduct().getSku(),
                lot.getProduct().getName(),
                lot.getExpiryDate(),
                lot.getSupplierId(),
                lot.getCreatedAt()
        );

        // Movimentações cronológicas
        List<StockMovement> movements = stockMovementRepository
                .findByTenantIdAndLotIdOrderByCreatedAtAsc(tenantId, lotId);

        List<MovimentacaoItem> movimentacoes = movements.stream()
                .map(m -> new MovimentacaoItem(
                        m.getId(),
                        m.getType(),
                        m.getQuantity(),
                        m.getSourceLocation() != null ? m.getSourceLocation().getCode() : null,
                        m.getDestinationLocation() != null ? m.getDestinationLocation().getCode() : null,
                        m.getOperatorId(),
                        m.getCreatedAt()
                ))
                .collect(Collectors.toList());

        // Localização atual (saldo remanescente do lote)
        List<StockItem> currentItems = stockItemRepository
                .findByFilters(tenantId, lot.getProduct().getId(), null, lotId, null)
                .stream()
                .filter(s -> s.getQuantityAvailable() > 0)
                .collect(Collectors.toList());

        LocalizacaoAtual localizacaoAtual = null;
        if (!currentItems.isEmpty()) {
            StockItem si = currentItems.get(0);
            localizacaoAtual = new LocalizacaoAtual(
                    si.getLocation().getId(),
                    si.getLocation().getCode(),
                    si.getQuantityAvailable()
            );
        }

        // Expedições (movimentos de SHIPPING com esse lote)
        List<ExpedicaoItem> expedicoes = movements.stream()
                .filter(m -> m.getType() == MovementType.SHIPPING
                          || m.getType() == MovementType.OUTBOUND)
                .filter(m -> m.getReferenceId() != null)
                .map(m -> new ExpedicaoItem(
                        m.getReferenceId(),
                        m.getReferenceNumber(),
                        m.getQuantity(),
                        m.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new RastreabilidadeLoteResponse(loteInfo, movimentacoes, localizacaoAtual, expedicoes);
    }
}
