package com.odin.wms.gs1;

import com.odin.wms.dto.response.GS1GeneratedResponse;
import com.odin.wms.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Gerador de códigos GS1 (GS1-128, SSCC, EAN-13 formatado).
 * AC6 — GET /gs1/generate
 */
@Component
public class GS1Generator {

    private static final DateTimeFormatter GS1_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    @Value("${wms.gs1.company-prefix:0789123}")
    private String companyPrefix;

    /**
     * Gera representações GS1 a partir dos parâmetros fornecidos.
     *
     * @param gtin         GTIN-14 (14 dígitos) — obrigatório
     * @param lotNumber    número do lote — opcional
     * @param serialNumber número de série — opcional
     * @param expiryDate   data de validade — opcional
     */
    public GS1GeneratedResponse generate(String gtin, String lotNumber,
                                          String serialNumber, LocalDate expiryDate) {
        validateGtin(gtin);

        String ean13      = buildEan13(gtin);
        String gs1128     = buildGs1128(gtin, lotNumber, expiryDate, serialNumber);
        String sscc       = serialNumber != null ? buildSscc(serialNumber) : null;
        String human      = buildHumanReadable(gtin, lotNumber, expiryDate, serialNumber);

        return new GS1GeneratedResponse(ean13, gs1128, sscc, human);
    }

    // -------------------------------------------------------------------------
    // Private builders
    // -------------------------------------------------------------------------

    private String buildEan13(String gtin14) {
        // EAN-13 = últimos 13 dígitos do GTIN-14 (remove leading zero)
        return gtin14.substring(1);
    }

    private String buildGs1128(String gtin, String lot, LocalDate expiry, String serial) {
        StringBuilder sb = new StringBuilder();
        sb.append("(01)").append(gtin);
        if (lot != null && !lot.isBlank()) {
            sb.append("(10)").append(lot);
        }
        if (expiry != null) {
            sb.append("(17)").append(expiry.format(GS1_DATE_FORMAT));
        }
        if (serial != null && !serial.isBlank()) {
            sb.append("(21)").append(serial);
        }
        return sb.toString();
    }

    /**
     * Gera SSCC-18: (00) + companyPrefix + serialRef + checkDigit.
     * Para simplificação, usa o serialNumber como referência de série.
     */
    private String buildSscc(String serialNumber) {
        // SSCC = prefixo empresa (7-9 dígitos) + referência (9-11 dígitos) + check digit = 18
        String numericSerial = serialNumber.replaceAll("[^0-9]", "");
        // Preenche até 10 dígitos de referência
        String ref = String.format("%010d", numericSerial.isEmpty() ? 0
                : Long.parseLong(numericSerial.substring(0, Math.min(numericSerial.length(), 10))));
        String body = companyPrefix + ref;
        // Trunca ou padeia para 17 dígitos antes do check digit
        if (body.length() > 17) body = body.substring(0, 17);
        while (body.length() < 17) body = body + "0";
        String checkDigit = String.valueOf(computeCheckDigit(body));
        return "(00)" + body + checkDigit;
    }

    private String buildHumanReadable(String gtin, String lot, LocalDate expiry, String serial) {
        StringBuilder sb = new StringBuilder();
        sb.append("[GTIN] ").append(gtin);
        if (lot != null && !lot.isBlank()) {
            sb.append(" [LOTE] ").append(lot);
        }
        if (expiry != null) {
            sb.append(" [VALIDADE] ").append(expiry.format(GS1_DATE_FORMAT));
        }
        if (serial != null && !serial.isBlank()) {
            sb.append(" [SÉRIE] ").append(serial);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Validation + check digit
    // -------------------------------------------------------------------------

    private void validateGtin(String gtin) {
        if (gtin == null || gtin.length() != 14) {
            throw new BusinessException(
                    "GTIN deve ter exatamente 14 dígitos. Fornecido: " + (gtin == null ? "null" : gtin.length()));
        }
        if (!gtin.matches("[0-9]{14}")) {
            throw new BusinessException("GTIN deve conter apenas dígitos: " + gtin);
        }
        if (!GS1Parser.validateCheckDigit(gtin)) {
            throw new BusinessException("GTIN com check digit inválido: " + gtin);
        }
    }

    private int computeCheckDigit(String digits) {
        int sum = 0;
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            int d = digits.charAt(i) - '0';
            int posFromRight = len - i;
            sum += d * (posFromRight % 2 == 0 ? 1 : 3);
        }
        return (10 - (sum % 10)) % 10;
    }
}
