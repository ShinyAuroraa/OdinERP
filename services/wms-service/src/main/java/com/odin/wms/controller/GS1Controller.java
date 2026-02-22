package com.odin.wms.controller;

import com.odin.wms.dto.request.GS1ParseRequest;
import com.odin.wms.dto.response.GS1GeneratedResponse;
import com.odin.wms.dto.response.GS1ParsedResponse;
import com.odin.wms.gs1.GS1Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Endpoints utilitários de parsing e geração de códigos GS1.
 * AC5 (POST /gs1/parse) e AC6 (GET /gs1/generate).
 * Acessível para todas as roles WMS autenticadas.
 */
@RestController
@RequestMapping("/api/v1/gs1")
@RequiredArgsConstructor
public class GS1Controller {

    private final GS1Service gs1Service;

    /**
     * POST /api/v1/gs1/parse
     * Faz parsing de um código de barras GS1.
     * AC5 — retorna 400 se formato inválido ou AI não reconhecido.
     */
    @PostMapping("/parse")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public GS1ParsedResponse parseBarcode(@Valid @RequestBody GS1ParseRequest request) {
        return gs1Service.parse(request);
    }

    /**
     * GET /api/v1/gs1/generate
     * Gera código GS1 a partir dos parâmetros fornecidos.
     * AC6 — retorna 400 se gtin inválido (não-14 dígitos ou check digit errado).
     */
    @GetMapping("/generate")
    @PreAuthorize("hasAnyRole('WMS_OPERATOR', 'WMS_SUPERVISOR', 'WMS_ADMIN')")
    public GS1GeneratedResponse generateBarcode(
            @RequestParam String gtin,
            @RequestParam(required = false) String lotNumber,
            @RequestParam(required = false) String serialNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {

        return gs1Service.generate(gtin, lotNumber, serialNumber, expiryDate);
    }
}
