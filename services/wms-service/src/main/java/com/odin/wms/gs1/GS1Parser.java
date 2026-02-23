package com.odin.wms.gs1;

import com.odin.wms.dto.response.GS1ParsedResponse;
import com.odin.wms.dto.response.GS1ParsedResponse.AIValue;
import com.odin.wms.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser de códigos de barras GS1.
 * Suporta: EAN13, GS1_128, SSCC, QR_CODE.
 *
 * Formatos:
 * - EAN13: string de 13 dígitos com check digit
 * - GS1_128: "(AI)VALUE(AI)VALUE..." com parênteses como delimitadores legíveis
 * - SSCC: "(00)" + 18 dígitos
 * - QR_CODE: mesmo formato que GS1_128
 */
@Component
public class GS1Parser {

    private static final String FORMAT_EAN13   = "EAN13";
    private static final String FORMAT_GS1_128 = "GS1_128";
    private static final String FORMAT_SSCC    = "SSCC";
    private static final String FORMAT_QR_CODE = "QR_CODE";

    public GS1ParsedResponse parse(String barcode, String format) {
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("Código de barras não pode ser vazio");
        }
        return switch (format.toUpperCase()) {
            case FORMAT_EAN13   -> parseEan13(barcode);
            case FORMAT_GS1_128, FORMAT_QR_CODE -> parseGS1128(barcode, format.toUpperCase());
            case FORMAT_SSCC    -> parseSscc(barcode);
            default -> throw new BusinessException("Formato não suportado: " + format
                    + ". Formatos aceitos: EAN13, GS1_128, SSCC, QR_CODE");
        };
    }

    // -------------------------------------------------------------------------
    // EAN-13
    // -------------------------------------------------------------------------

    private GS1ParsedResponse parseEan13(String barcode) {
        String digits = barcode.replaceAll("[^0-9]", "");
        if (digits.length() != 13) {
            throw new BusinessException("EAN-13 deve ter exatamente 13 dígitos. Encontrado: " + digits.length());
        }
        if (!validateCheckDigit(digits)) {
            throw new BusinessException("EAN-13 com check digit inválido: " + digits);
        }
        // EAN-13 → GTIN-14 com leading zero
        String gtin14 = "0" + digits;
        Map<String, AIValue> ais = Map.of("01", new AIValue("GTIN", gtin14));
        return new GS1ParsedResponse(FORMAT_EAN13, ais, gtin14, null, null, null);
    }

    // -------------------------------------------------------------------------
    // GS1-128 / QR Code
    // -------------------------------------------------------------------------

    private GS1ParsedResponse parseGS1128(String barcode, String format) {
        Map<String, AIValue> ais = extractAIs(barcode);
        if (ais.isEmpty()) {
            throw new BusinessException("Nenhum Application Identifier reconhecido no código: " + barcode);
        }
        String gtin       = getAIValue(ais, "01");
        String lot        = getAIValue(ais, "10");
        String serial     = getAIValue(ais, "21");
        LocalDate expiry  = parseExpiryDate(getAIValue(ais, "17"));

        if (gtin != null && !validateCheckDigit(gtin)) {
            throw new BusinessException("GTIN com check digit inválido: " + gtin);
        }
        return new GS1ParsedResponse(format, ais, gtin, lot, serial, expiry);
    }

    // -------------------------------------------------------------------------
    // SSCC
    // -------------------------------------------------------------------------

    private GS1ParsedResponse parseSscc(String barcode) {
        Map<String, AIValue> ais = extractAIs(barcode);
        AIValue ssccAI = ais.get("00");
        if (ssccAI == null) {
            throw new BusinessException("SSCC deve começar com AI (00). Formato: (00)XXXXXXXXXXXXXXXXXX");
        }
        if (ssccAI.value().length() != 18) {
            throw new BusinessException("SSCC deve ter exatamente 18 dígitos. Encontrado: " + ssccAI.value().length());
        }
        return new GS1ParsedResponse(FORMAT_SSCC, ais, null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // AI extraction — formato "(AI)VALUE(AI)VALUE..."
    // -------------------------------------------------------------------------

    /**
     * Extrai Application Identifiers do formato legível "(AI)VALUE(AI)VALUE...".
     * Trata tanto o formato com parênteses quanto sem (concatenado puro).
     */
    Map<String, AIValue> extractAIs(String barcode) {
        Map<String, AIValue> result = new LinkedHashMap<>();
        int i = 0;
        while (i < barcode.length()) {
            if (barcode.charAt(i) == '(') {
                int close = barcode.indexOf(')', i);
                if (close == -1) {
                    throw new BusinessException("Código malformado — parêntese de fechamento ausente: " + barcode);
                }
                String aiCode = barcode.substring(i + 1, close);
                GS1ApplicationIdentifier ai = GS1ApplicationIdentifier.KNOWN_AIS.get(aiCode);
                if (ai == null) {
                    throw new BusinessException("Application Identifier não reconhecido: " + aiCode);
                }
                i = close + 1;
                int end = findValueEnd(barcode, i, ai);
                String value = barcode.substring(i, end);
                result.put(aiCode, new AIValue(ai.name(), value));
                i = end;
            } else {
                // Skip espaços ou outros caracteres
                i++;
            }
        }
        return result;
    }

    private int findValueEnd(String barcode, int start, GS1ApplicationIdentifier ai) {
        if (!ai.variableLength()) {
            // Comprimento fixo
            return Math.min(start + ai.maxLength(), barcode.length());
        }
        // Comprimento variável: termina no próximo '(' ou no fim da string
        int nextParen = barcode.indexOf('(', start);
        return nextParen == -1 ? barcode.length() : nextParen;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String getAIValue(Map<String, AIValue> ais, String code) {
        AIValue v = ais.get(code);
        return v != null ? v.value() : null;
    }

    /**
     * Valida check digit GS1 para GTIN-8/12/13/14 e EAN-13.
     * Algoritmo: alternância 3/1 da esquerda, posições ímpares × 3, pares × 1.
     */
    public static boolean validateCheckDigit(String digits) {
        if (digits == null || digits.length() < 2) return false;
        int sum = 0;
        int len = digits.length();
        for (int i = 0; i < len - 1; i++) {
            int d = digits.charAt(i) - '0';
            // Posição a partir do lado direito excluindo check digit = len-1-i
            // GS1: posição ímpar da direita (1-indexed) recebe peso 3
            int posFromRight = len - 1 - i;
            sum += d * (posFromRight % 2 == 0 ? 1 : 3);
        }
        int expected = (10 - (sum % 10)) % 10;
        return expected == (digits.charAt(len - 1) - '0');
    }

    /**
     * Converte data GS1 no formato YYMMDD para LocalDate.
     * GS1 standard: 00-49 = 2000-2049, 50-99 = 1950-1999.
     * Dia 00 = último dia do mês (GS1 convention para alguns AIs).
     */
    public static LocalDate parseExpiryDate(String yymmdd) {
        if (yymmdd == null || yymmdd.length() != 6) return null;
        int year  = Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day   = Integer.parseInt(yymmdd.substring(4, 6));
        int fullYear = year < 50 ? 2000 + year : 1900 + year;
        if (day == 0) {
            // GS1 convention: day=00 → último dia do mês
            return LocalDate.of(fullYear, month, 1).withDayOfMonth(
                    LocalDate.of(fullYear, month, 1).lengthOfMonth());
        }
        return LocalDate.of(fullYear, month, day);
    }
}
