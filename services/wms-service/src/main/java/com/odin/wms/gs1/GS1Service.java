package com.odin.wms.gs1;

import com.odin.wms.dto.request.GS1ParseRequest;
import com.odin.wms.dto.response.GS1GeneratedResponse;
import com.odin.wms.dto.response.GS1ParsedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Orchestrator do parsing e geração de códigos GS1.
 * Delega para GS1Parser (parsing) e GS1Generator (geração).
 */
@Service
@RequiredArgsConstructor
public class GS1Service {

    private final GS1Parser parser;
    private final GS1Generator generator;

    /**
     * Faz parsing de um código de barras GS1 extraindo todos os AIs.
     * Lança BusinessException (400) se formato inválido ou AI não reconhecido.
     */
    public GS1ParsedResponse parse(GS1ParseRequest request) {
        return parser.parse(request.barcode(), request.format());
    }

    /**
     * Gera representações GS1 a partir dos parâmetros fornecidos.
     * Lança BusinessException (400) se GTIN inválido.
     */
    public GS1GeneratedResponse generate(String gtin, String lotNumber,
                                          String serialNumber, LocalDate expiryDate) {
        return generator.generate(gtin, lotNumber, serialNumber, expiryDate);
    }
}
