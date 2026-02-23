package com.odin.wms.report;

import com.odin.wms.domain.entity.Lot;
import com.odin.wms.domain.entity.ProductWms;
import com.odin.wms.domain.entity.StockItem;
import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.repository.LotRepository;
import com.odin.wms.domain.repository.ProductWmsRepository;
import com.odin.wms.domain.repository.StockItemRepository;
import com.odin.wms.domain.repository.StockMovementRepository;
import com.odin.wms.dto.report.AnvisaRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gera linhas para o relatório ANVISA Vigilância Sanitária.
 * Retorna apenas produtos onde vigilanciaSanitaria = true.
 */
@Component
@RequiredArgsConstructor
public class AnvisaReportGenerator {

    private final ProductWmsRepository productWmsRepository;
    private final LotRepository lotRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;

    public List<AnvisaRow> generate(
            UUID warehouseId,
            LocalDate dataInicio,
            LocalDate dataFim,
            UUID productIdFilter,
            UUID lotIdFilter,
            UUID tenantId
    ) {
        Instant from = dataInicio.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = dataFim.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Busca produtos com vigilância sanitária habilitada
        List<ProductWms> vigilanciaProducts = productWmsRepository
                .findByTenantIdAndVigilanciaSanitaria(tenantId, true);

        if (productIdFilter != null) {
            vigilanciaProducts = vigilanciaProducts.stream()
                    .filter(p -> p.getId().equals(productIdFilter))
                    .collect(Collectors.toList());
        }

        List<AnvisaRow> rows = new ArrayList<>();

        for (ProductWms product : vigilanciaProducts) {
            // Lotes do produto
            List<Lot> lots = lotRepository.findByTenantIdAndProductId(tenantId, product.getId());
            if (lotIdFilter != null) {
                lots = lots.stream()
                        .filter(l -> l.getId().equals(lotIdFilter))
                        .collect(Collectors.toList());
            }

            for (Lot lot : lots) {
                // Movimentos do lote no período
                List<StockMovement> movements = stockMovementRepository
                        .findByTenantIdAndLotIdOrderByCreatedAtAsc(tenantId, lot.getId())
                        .stream()
                        .filter(m -> !m.getCreatedAt().isBefore(from) && m.getCreatedAt().isBefore(to))
                        .collect(Collectors.toList());

                if (movements.isEmpty()) continue;

                int quantityReceived   = movements.stream()
                        .filter(m -> m.getType() == MovementType.INBOUND)
                        .mapToInt(StockMovement::getQuantity).sum();
                int quantityExpedited  = movements.stream()
                        .filter(m -> m.getType() == MovementType.SHIPPING
                                  || m.getType() == MovementType.OUTBOUND)
                        .mapToInt(StockMovement::getQuantity).sum();

                // Saldo atual do lote
                int currentBalance = stockItemRepository
                        .findByFilters(tenantId, product.getId(), null, lot.getId(), warehouseId)
                        .stream().mapToInt(StockItem::getQuantityAvailable).sum();

                Instant receivedAt = movements.stream()
                        .filter(m -> m.getType() == MovementType.INBOUND)
                        .map(StockMovement::getCreatedAt)
                        .min(Comparator.naturalOrder())
                        .orElse(lot.getCreatedAt());

                rows.add(new AnvisaRow(
                        product.getId(),
                        product.getSku(),
                        product.getName(),
                        lot.getLotNumber(),
                        lot.getExpiryDate(),
                        quantityReceived,
                        quantityExpedited,
                        currentBalance,
                        lot.getSupplierId(),
                        receivedAt
                ));
            }
        }

        return rows;
    }
}
