package com.odin.wms.dto.report;

import com.odin.wms.domain.enums.MovementType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resposta completa de rastreabilidade de lote para recall.
 * Inclui info do lote, movimentações cronológicas, localização atual e expedições.
 */
public record RastreabilidadeLoteResponse(
        LoteInfo loteInfo,
        List<MovimentacaoItem> movimentacoes,
        LocalizacaoAtual localizacaoAtual,
        List<ExpedicaoItem> expedicoes
) {

    public record LoteInfo(
            UUID lotId,
            String lotNumber,
            UUID productId,
            String sku,
            String productName,
            LocalDate expiryDate,
            UUID supplierId,
            Instant receivedAt
    ) {}

    public record MovimentacaoItem(
            UUID movementId,
            MovementType movementType,
            int quantity,
            String sourceLocation,
            String destinationLocation,
            UUID operatorId,
            Instant createdAt
    ) {}

    public record LocalizacaoAtual(
            UUID locationId,
            String locationCode,
            int quantityAvailable
    ) {}

    public record ExpedicaoItem(
            UUID referenceId,
            String referenceNumber,
            int quantityExpedited,
            Instant expedidoEm
    ) {}
}
