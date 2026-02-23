package com.odin.wms.dto.report;

import java.util.UUID;

/**
 * Linha do relatório Ficha de Estoque (Receita Federal).
 * Representa o resumo de movimentações de um produto no período.
 */
public record FichaEstoqueRow(
        UUID productId,
        String sku,
        String productName,
        UUID warehouseId,
        String periodoInicio,
        String periodoFim,
        int saldoInicial,
        int entradas,
        int saidas,
        int ajustes,
        int saldoFinal
) {}
