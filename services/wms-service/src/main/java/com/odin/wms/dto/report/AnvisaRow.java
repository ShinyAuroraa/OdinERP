package com.odin.wms.dto.report;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * Linha do relatório ANVISA Vigilância Sanitária.
 * Retorna apenas produtos com vigilanciaSanitaria = true.
 */
public record AnvisaRow(
        UUID productId,
        String sku,
        String productName,
        String lotNumber,
        LocalDate expiryDate,
        int quantityReceived,
        int quantityExpedited,
        int currentBalance,
        UUID supplierId,
        Instant receivedAt
) {}
