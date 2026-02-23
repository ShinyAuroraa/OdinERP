package com.odin.wms.gs1;

import java.util.Map;

/**
 * Application Identifiers GS1 suportados.
 * Ref: GS1 General Specifications v24 — seção 3.2.
 *
 * fixedLength=true  → comprimento exato (sem FNC1 necessário)
 * fixedLength=false → comprimento variável (até maxLength; termina em FNC1 em GS1-128)
 */
public record GS1ApplicationIdentifier(String name, int maxLength, boolean variableLength) {

    /** Mapeamento completo dos AIs suportados nesta implementação. */
    public static final Map<String, GS1ApplicationIdentifier> KNOWN_AIS = Map.of(
            "00", new GS1ApplicationIdentifier("SSCC",             18, false),
            "01", new GS1ApplicationIdentifier("GTIN",             14, false),
            "10", new GS1ApplicationIdentifier("BATCH_LOT",        20, true),
            "11", new GS1ApplicationIdentifier("PRODUCTION_DATE",   6, false),
            "17", new GS1ApplicationIdentifier("EXPIRY_DATE",       6, false),
            "21", new GS1ApplicationIdentifier("SERIAL_NUMBER",    20, true)
    );
}
