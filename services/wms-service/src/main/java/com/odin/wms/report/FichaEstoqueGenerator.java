package com.odin.wms.report;

import com.odin.wms.domain.entity.StockItem;
import com.odin.wms.domain.entity.StockMovement;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.repository.StockItemRepository;
import com.odin.wms.domain.repository.StockMovementRepository;
import com.odin.wms.dto.report.FichaEstoqueRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gera linhas para o relatório Ficha de Estoque (Receita Federal).
 * Calcula: saldoInicial, entradas, saídas, ajustes, saldoFinal por produto.
 */
@Component
@RequiredArgsConstructor
public class FichaEstoqueGenerator {

    private final StockMovementRepository stockMovementRepository;
    private final StockItemRepository stockItemRepository;

    public List<FichaEstoqueRow> generate(
            UUID warehouseId,
            LocalDate dataInicio,
            LocalDate dataFim,
            UUID productIdFilter,
            UUID tenantId
    ) {
        Instant from = dataInicio.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = dataFim.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Busca todos os movimentos do período no warehouse
        List<StockMovement> allMovements = stockMovementRepository
                .findByTenantIdAndWarehouseIdAndPeriod(tenantId, warehouseId, from, to);

        // Agrupa por produto
        Map<UUID, List<StockMovement>> byProduct = allMovements.stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()));

        // Para filtro por produto específico
        if (productIdFilter != null) {
            byProduct = byProduct.entrySet().stream()
                    .filter(e -> e.getKey().equals(productIdFilter))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        List<FichaEstoqueRow> rows = new ArrayList<>();
        String inicio = dataInicio.toString();
        String fim    = dataFim.toString();

        for (Map.Entry<UUID, List<StockMovement>> entry : byProduct.entrySet()) {
            UUID prodId   = entry.getKey();
            List<StockMovement> movements = entry.getValue();
            StockMovement sample = movements.get(0);

            int entradas = 0;
            int saidas   = 0;
            int ajustes  = 0;

            for (StockMovement m : movements) {
                switch (m.getType()) {
                    case INBOUND -> entradas += m.getQuantity();
                    case PICKING, SHIPPING, OUTBOUND -> saidas += m.getQuantity();
                    case ADJUSTMENT, INVENTORY_ADJUSTMENT -> ajustes += m.getQuantity();
                    default -> { /* TRANSFER, PUTAWAY, PACKING etc — não afetam saldo líquido */ }
                }
            }

            // Saldo atual disponível nesse produto + warehouse
            int saldoAtual = stockItemRepository
                    .findByFilters(tenantId, prodId, null, null, warehouseId)
                    .stream().mapToInt(StockItem::getQuantityAvailable).sum();

            // saldoInicial = saldoAtual - entradas + saidas - ajustes
            int saldoInicial = saldoAtual - entradas + saidas - ajustes;
            int saldoFinal   = saldoInicial + entradas - saidas + ajustes;

            rows.add(new FichaEstoqueRow(
                    prodId,
                    sample.getProduct().getSku(),
                    sample.getProduct().getName(),
                    warehouseId,
                    inicio,
                    fim,
                    Math.max(0, saldoInicial),
                    entradas,
                    saidas,
                    ajustes,
                    saldoFinal
            ));
        }

        return rows;
    }
}
