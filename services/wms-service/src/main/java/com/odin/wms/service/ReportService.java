package com.odin.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.repository.WarehouseRepository;
import com.odin.wms.report.*;
import com.odin.wms.report.export.ReportExportStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço principal de geração de relatórios regulatórios.
 * Resolve a strategy de exportação via Map&lt;ExportFormat, ReportExportStrategy&gt;.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int MAX_PERIOD_DAYS = 365;
    private static final DateTimeFormatter FILENAME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FichaEstoqueGenerator fichaEstoqueGenerator;
    private final AnvisaReportGenerator anvisaReportGenerator;
    private final RastreabilidadeGenerator rastreabilidadeGenerator;
    private final MovimentacoesGenerator movimentacoesGenerator;
    private final WarehouseRepository warehouseRepository;
    private final ObjectMapper objectMapper;
    private final List<ReportExportStrategy> strategies;

    private Map<ExportFormat, ReportExportStrategy> strategyMap;

    @PostConstruct
    public void buildStrategyMap() {
        strategyMap = new EnumMap<>(ExportFormat.class);
        strategies.forEach(s -> strategyMap.put(s.getFormat(), s));
    }

    // -------------------------------------------------------------------------
    // Ficha de Estoque (AC1)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResponseEntity<?> generateFichaEstoque(
            UUID warehouseId, LocalDate dataInicio, LocalDate dataFim,
            UUID productId, ExportFormat format, UUID tenantId
    ) {
        validatePeriod(dataInicio, dataFim);
        validateWarehouseTenant(warehouseId, tenantId);

        var rows = fichaEstoqueGenerator.generate(warehouseId, dataInicio, dataFim, productId, tenantId);
        String period = dataInicio + " a " + dataFim;
        return buildResponse(rows, "Ficha de Estoque", period, format, "ficha-estoque");
    }

    // -------------------------------------------------------------------------
    // ANVISA (AC2)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResponseEntity<?> generateAnvisa(
            UUID warehouseId, LocalDate dataInicio, LocalDate dataFim,
            UUID productId, UUID lotId, ExportFormat format, UUID tenantId
    ) {
        validatePeriod(dataInicio, dataFim);
        validateWarehouseTenant(warehouseId, tenantId);

        var rows = anvisaReportGenerator.generate(warehouseId, dataInicio, dataFim, productId, lotId, tenantId);
        String period = dataInicio + " a " + dataFim;
        return buildResponse(rows, "ANVISA Vigilância Sanitária", period, format, "anvisa");
    }

    // -------------------------------------------------------------------------
    // Rastreabilidade por Lote (AC3)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResponseEntity<?> generateRastreabilidadeLote(UUID lotId, ExportFormat format, UUID tenantId) {
        var response = rastreabilidadeGenerator.generate(lotId, tenantId);
        return buildResponse(List.of(response), "Rastreabilidade Lote", "", format, "rastreabilidade-lote");
    }

    // -------------------------------------------------------------------------
    // Movimentações (AC4)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ResponseEntity<?> generateMovimentacoes(
            UUID warehouseId, LocalDate dataInicio, LocalDate dataFim,
            UUID productId, MovementType movementType, UUID locationId,
            ExportFormat format, UUID tenantId, Pageable pageable
    ) {
        validatePeriod(dataInicio, dataFim);
        validateWarehouseTenant(warehouseId, tenantId);

        Page<?> page = movimentacoesGenerator.generate(
                warehouseId, dataInicio, dataFim, productId, movementType, locationId, tenantId, pageable);

        // Para JSON retorna Page; para outros formatos usa o conteúdo como lista
        if (format == ExportFormat.JSON) {
            return ResponseEntity.ok(page);
        }
        String period = dataInicio + " a " + dataFim;
        return buildResponse(page.getContent(), "Movimentações", period, format, "movimentacoes");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("dataInicio e dataFim são obrigatórios");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("dataInicio não pode ser posterior a dataFim");
        }
        if (from.until(to, java.time.temporal.ChronoUnit.DAYS) > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException(
                    "Período máximo é de " + MAX_PERIOD_DAYS + " dias");
        }
    }

    private void validateWarehouseTenant(UUID warehouseId, UUID tenantId) {
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Warehouse não encontrado: " + warehouseId));
        if (!warehouse.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException(
                    "Warehouse não pertence ao tenant: " + warehouseId);
        }
    }

    private ResponseEntity<?> buildResponse(
            List<?> rows, String title, String period, ExportFormat format, String filenameBase
    ) {
        ReportExportStrategy strategy = strategyMap.get(format);
        if (strategy == null) {
            throw new IllegalArgumentException("Formato de exportação inválido: " + format);
        }

        try {
            byte[] data = strategy.export(rows, title, period, objectMapper);
            String date = LocalDate.now().format(FILENAME_FMT);

            return switch (format) {
                case JSON -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(rows);
                case PDF -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"relatorio-" + filenameBase + "-" + date + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(data);
                case EXCEL -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"relatorio-" + filenameBase + "-" + date + ".xlsx\"")
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(data);
                case XML -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .body(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            };
        } catch (IOException e) {
            log.error("Erro ao exportar relatório '{}' no formato {}: {}", title, format, e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar relatório no formato " + format, e);
        }
    }
}
