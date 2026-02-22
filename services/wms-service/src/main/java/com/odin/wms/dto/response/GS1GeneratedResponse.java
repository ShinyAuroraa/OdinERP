package com.odin.wms.dto.response;

/**
 * Resposta da geração de códigos GS1.
 * AC6 — GET /gs1/generate
 *
 * sscc é null quando serialNumber não é fornecido.
 */
public record GS1GeneratedResponse(
        String ean13,
        String gs1128,
        String sscc,
        String humanReadable
) {}
