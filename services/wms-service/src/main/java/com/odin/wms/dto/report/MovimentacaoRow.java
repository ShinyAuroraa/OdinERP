package com.odin.wms.dto.report;

import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReferenceType;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha do relatório de Movimentações por Período.
 */
public record MovimentacaoRow(
        UUID movementId,
        MovementType movementType,
        ReferenceType referenceType,
        UUID referenceId,
        UUID productId,
        String sku,
        UUID lotId,
        UUID locationId,
        int quantity,
        UUID performedBy,
        Instant createdAt
) {}
