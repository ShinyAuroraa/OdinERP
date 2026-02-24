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
 *
 * Fix QA-7.1-TD-002: refatorado de padrão O(N×M) queries para O(1) = 4 queries bulk.
 * - Query 1: produtos com vigilânciaSanitaria=true do tenant
 * - Query 2: todos os lotes desses produtos (IN clause)
 * - Query 3: todos os movimentos dos lotes no período (IN clause)
 * - Query 4: saldo atual de todos os lotes (IN clause — reutiliza findAvailableByTenantIdAndLotIdIn)
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

        // Query 1: produtos com vigilância sanitária habilitada
        List<ProductWms> vigilanciaProducts = productWmsRepository
                .findByTenantIdAndVigilanciaSanitaria(tenantId, true);

        if (vigilanciaProducts.isEmpty()) return List.of();

        if (productIdFilter != null) {
            vigilanciaProducts = vigilanciaProducts.stream()
                    .filter(p -> p.getId().equals(productIdFilter))
                    .collect(Collectors.toList());
        }

        if (vigilanciaProducts.isEmpty()) return List.of();

        List<UUID> productIds = vigilanciaProducts.stream()
                .map(ProductWms::getId)
                .collect(Collectors.toList());

        // Query 2: todos os lotes desses produtos em uma única query
        List<Lot> allLots = lotRepository.findByTenantIdAndProductIdIn(tenantId, productIds);

        if (lotIdFilter != null) {
            allLots = allLots.stream()
                    .filter(l -> l.getId().equals(lotIdFilter))
                    .collect(Collectors.toList());
        }

        if (allLots.isEmpty()) return List.of();

        List<UUID> lotIds = allLots.stream()
                .map(Lot::getId)
                .collect(Collectors.toList());

        // Query 3: todos os movimentos dos lotes no período em uma única query
        List<StockMovement> allMovements = stockMovementRepository
                .findByTenantIdAndLotIdInAndCreatedAtBetween(tenantId, lotIds, from, to);

        // Query 4: saldo atual de todos os lotes em uma única query
        List<StockItem> allStockItems = stockItemRepository
                .findAvailableByTenantIdAndLotIdIn(tenantId, lotIds);

        // Agrupamento em memória
        Map<UUID, List<StockMovement>> movementsByLotId = allMovements.stream()
                .filter(m -> m.getLot() != null)
                .collect(Collectors.groupingBy(m -> m.getLot().getId()));

        Map<UUID, Integer> balanceByLotId = allStockItems.stream()
                .filter(s -> s.getLot() != null)
                .collect(Collectors.toMap(
                        s -> s.getLot().getId(),
                        StockItem::getQuantityAvailable,
                        Integer::sum
                ));

        Map<UUID, ProductWms> productById = vigilanciaProducts.stream()
                .collect(Collectors.toMap(ProductWms::getId, p -> p));

        List<AnvisaRow> rows = new ArrayList<>();

        for (Lot lot : allLots) {
            List<StockMovement> movements = movementsByLotId.getOrDefault(lot.getId(), List.of());
            if (movements.isEmpty()) continue;

            ProductWms product = productById.get(lot.getProduct().getId());
            if (product == null) continue;

            int quantityReceived = movements.stream()
                    .filter(m -> m.getType() == MovementType.INBOUND)
                    .mapToInt(StockMovement::getQuantity).sum();

            int quantityExpedited = movements.stream()
                    .filter(m -> m.getType() == MovementType.SHIPPING
                              || m.getType() == MovementType.OUTBOUND)
                    .mapToInt(StockMovement::getQuantity).sum();

            int currentBalance = balanceByLotId.getOrDefault(lot.getId(), 0);

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

        return rows;
    }
}
