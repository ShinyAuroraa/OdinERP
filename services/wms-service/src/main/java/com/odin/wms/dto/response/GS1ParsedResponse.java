package com.odin.wms.dto.response;

import java.time.LocalDate;
import java.util.Map;

/**
 * Resultado do parsing de um código de barras GS1.
 * AC5 — POST /gs1/parse
 */
public record GS1ParsedResponse(
        String format,
        Map<String, AIValue> applicationIdentifiers,
        String gtin,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate
) {

    /**
     * Valor de um Application Identifier extraído do código de barras.
     */
    public record AIValue(String name, String value) {}
}
