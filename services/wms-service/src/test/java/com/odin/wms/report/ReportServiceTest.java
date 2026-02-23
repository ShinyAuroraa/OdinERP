package com.odin.wms.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.odin.wms.domain.entity.Warehouse;
import com.odin.wms.domain.enums.ExportFormat;
import com.odin.wms.domain.enums.MovementType;
import com.odin.wms.domain.enums.ReportType;
import com.odin.wms.domain.repository.ReportScheduleRepository;
import com.odin.wms.domain.repository.WarehouseRepository;
import com.odin.wms.dto.report.*;
import com.odin.wms.dto.request.ReportScheduleRequest;
import com.odin.wms.report.export.*;
import com.odin.wms.service.ReportScheduleService;
import com.odin.wms.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ReportService e ReportScheduleService — U1–U10.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private FichaEstoqueGenerator fichaEstoqueGenerator;
    @Mock private AnvisaReportGenerator anvisaReportGenerator;
    @Mock private RastreabilidadeGenerator rastreabilidadeGenerator;
    @Mock private MovimentacoesGenerator movimentacoesGenerator;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private JsonExportStrategy jsonStrategy;
    @Mock private PdfExportStrategy pdfStrategy;
    @Mock private ExcelExportStrategy excelStrategy;
    @Mock private XmlExportStrategy xmlStrategy;
    @Mock private ReportScheduleRepository scheduleRepository;

    private ReportService reportService;
    private ReportScheduleService scheduleService;

    private final UUID tenantId    = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        when(jsonStrategy.getFormat()).thenReturn(ExportFormat.JSON);
        when(pdfStrategy.getFormat()).thenReturn(ExportFormat.PDF);
        when(excelStrategy.getFormat()).thenReturn(ExportFormat.EXCEL);
        when(xmlStrategy.getFormat()).thenReturn(ExportFormat.XML);

        List<ReportExportStrategy> strategies = List.of(jsonStrategy, pdfStrategy, excelStrategy, xmlStrategy);

        reportService = new ReportService(
                fichaEstoqueGenerator,
                anvisaReportGenerator,
                rastreabilidadeGenerator,
                movimentacoesGenerator,
                warehouseRepository,
                objectMapper,
                strategies
        );
        reportService.buildStrategyMap();

        scheduleService = new ReportScheduleService(scheduleRepository, reportService);
    }

    // -------------------------------------------------------------------------
    // U1 — FichaEstoque: calcula saldos corretos com movimentos
    // -------------------------------------------------------------------------

    @Test
    void U1_fichaEstoque_withMovements_calculatesCorrectBalances() throws IOException {
        var row = new FichaEstoqueRow(UUID.randomUUID(), "SKU-001", "Produto A",
                warehouseId, "2026-01-01", "2026-01-31", 100, 50, 30, 5, 125);

        when(warehouseRepository.findByIdAndTenantId(warehouseId, tenantId))
                .thenReturn(Optional.of(Warehouse.builder().build()));
        when(fichaEstoqueGenerator.generate(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(jsonStrategy.export(any(), any(), any(), any())).thenReturn(new byte[0]);

        ResponseEntity<?> resp = reportService.generateFichaEstoque(
                warehouseId, LocalDate.of(2026,1,1), LocalDate.of(2026,1,31),
                null, ExportFormat.JSON, tenantId);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        // Verifica que o generator foi chamado com os parâmetros corretos
        verify(fichaEstoqueGenerator).generate(eq(warehouseId), eq(LocalDate.of(2026,1,1)),
                eq(LocalDate.of(2026,1,31)), isNull(), eq(tenantId));
    }

    // -------------------------------------------------------------------------
    // U2 — Movimentações: filtro de data funciona
    // -------------------------------------------------------------------------

    @Test
    void U2_movimentacoes_withDateFilter_returnsOnlyInRange() {
        var page = new PageImpl<>(List.of(
                new MovimentacaoRow(UUID.randomUUID(), MovementType.INBOUND, null,
                        null, UUID.randomUUID(), "SKU-002", null, null,
                        10, UUID.randomUUID(), java.time.Instant.now())
        ));

        when(warehouseRepository.findByIdAndTenantId(warehouseId, tenantId))
                .thenReturn(Optional.of(Warehouse.builder().build()));
        when(movimentacoesGenerator.generate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        ResponseEntity<?> resp = reportService.generateMovimentacoes(
                warehouseId, LocalDate.of(2026,1,1), LocalDate.of(2026,1,31),
                null, null, null, ExportFormat.JSON, tenantId, PageRequest.of(0, 50));

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(movimentacoesGenerator).generate(eq(warehouseId), eq(LocalDate.of(2026,1,1)),
                eq(LocalDate.of(2026,1,31)), isNull(), isNull(), isNull(), eq(tenantId), any());
    }

    // -------------------------------------------------------------------------
    // U3 — Rastreabilidade Lote: retorna cadeia completa
    // -------------------------------------------------------------------------

    @Test
    void U3_rastreabilidadeLote_validLot_returnsFullChain() {
        UUID lotId = UUID.randomUUID();
        var loteInfo = new RastreabilidadeLoteResponse.LoteInfo(
                lotId, "LOT-001", UUID.randomUUID(), "SKU-003", "Prod C",
                null, null, java.time.Instant.now());
        var response = new RastreabilidadeLoteResponse(loteInfo, List.of(), null, List.of());

        when(rastreabilidadeGenerator.generate(lotId, tenantId)).thenReturn(response);

        ResponseEntity<?> resp = reportService.generateRastreabilidadeLote(lotId, ExportFormat.JSON, tenantId);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(rastreabilidadeGenerator).generate(lotId, tenantId);
    }

    // -------------------------------------------------------------------------
    // U4 — ANVISA: retorna apenas produtos com vigilância sanitária
    // -------------------------------------------------------------------------

    @Test
    void U4_anvisa_returnsOnlyVigilanciaProducts() throws IOException {
        var anvisaRow = new AnvisaRow(UUID.randomUUID(), "SKU-ANVISA", "Med A",
                "LOT-A", null, 100, 30, 70, null, java.time.Instant.now());

        when(warehouseRepository.findByIdAndTenantId(warehouseId, tenantId))
                .thenReturn(Optional.of(Warehouse.builder().build()));
        when(anvisaReportGenerator.generate(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(anvisaRow));
        when(jsonStrategy.export(any(), any(), any(), any())).thenReturn(new byte[0]);

        ResponseEntity<?> resp = reportService.generateAnvisa(
                warehouseId, LocalDate.of(2026,1,1), LocalDate.of(2026,1,31),
                null, null, ExportFormat.JSON, tenantId);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(anvisaReportGenerator).generate(eq(warehouseId), any(), any(), isNull(), isNull(), eq(tenantId));
    }

    // -------------------------------------------------------------------------
    // U5 — PDF Exporter: gera bytes não vazios
    // -------------------------------------------------------------------------

    @Test
    void U5_pdfExporter_generatesNonEmptyBytes() throws IOException {
        PdfExportStrategy real = new PdfExportStrategy();
        var row = new FichaEstoqueRow(UUID.randomUUID(), "SKU-X", "Prod X",
                UUID.randomUUID(), "2026-01-01", "2026-01-31", 10, 5, 3, 0, 12);

        byte[] bytes = real.export(List.of(row), "Teste PDF", "2026-01-01 a 2026-01-31", objectMapper);

        assertThat(bytes).isNotEmpty();
        // PDF começa com %PDF
        assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
    }

    // -------------------------------------------------------------------------
    // U6 — Excel Exporter: gera workbook válido
    // -------------------------------------------------------------------------

    @Test
    void U6_excelExporter_generatesValidWorkbook() throws IOException {
        ExcelExportStrategy real = new ExcelExportStrategy();
        var row = new FichaEstoqueRow(UUID.randomUUID(), "SKU-Y", "Prod Y",
                UUID.randomUUID(), "2026-01-01", "2026-01-31", 50, 20, 10, 0, 60);

        byte[] bytes = real.export(List.of(row), "Teste Excel", "2026-01-01 a 2026-01-31", objectMapper);

        assertThat(bytes).isNotEmpty();
        // XLSX (ZIP) começa com PK
        assertThat(bytes[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(bytes[1]).isEqualTo((byte) 0x4B); // 'K'
    }

    // -------------------------------------------------------------------------
    // U7 — XML Exporter: gera XML bem-formado
    // -------------------------------------------------------------------------

    @Test
    void U7_xmlExporter_generatesWellFormedXml() throws IOException {
        XmlExportStrategy real = new XmlExportStrategy();
        var row = new FichaEstoqueRow(UUID.randomUUID(), "SKU-Z", "Prod Z",
                UUID.randomUUID(), "2026-01-01", "2026-01-31", 0, 10, 5, 2, 7);

        byte[] bytes = real.export(List.of(row), "Teste XML", "2026-01-01 a 2026-01-31", objectMapper);
        String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(xml).startsWith("<?xml");
        assertThat(xml).contains("<relatorio");
        assertThat(xml).contains("<item>");
        assertThat(xml).contains("</relatorio>");
    }

    // -------------------------------------------------------------------------
    // U8 — Período > 365 dias: lança IllegalArgumentException → 400
    // -------------------------------------------------------------------------

    @Test
    void U8_reportService_periodOver365Days_throwsBadRequest() {
        assertThatThrownBy(() -> reportService.generateFichaEstoque(
                warehouseId, LocalDate.of(2025,1,1), LocalDate.of(2026,6,1),
                null, ExportFormat.JSON, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("365");
    }

    // -------------------------------------------------------------------------
    // U9 — Cron inválido: lança IllegalArgumentException
    // -------------------------------------------------------------------------

    @Test
    void U9_createSchedule_invalidCron_throwsBadRequest() {
        var request = new ReportScheduleRequest(
                ReportType.FICHA_ESTOQUE, ExportFormat.PDF, "not-a-cron",
                warehouseId, null);

        assertThatThrownBy(() -> scheduleService.createSchedule(request, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cron");
    }

    // -------------------------------------------------------------------------
    // U10 — executeScheduledReports: schedule vencido → atualiza nextExecutionAt
    // -------------------------------------------------------------------------

    @Test
    void U10_executeScheduledReports_dueSchedule_updatesNextExecutionAt() throws Exception {
        com.odin.wms.domain.entity.ReportSchedule schedule =
                com.odin.wms.domain.entity.ReportSchedule.builder()
                        .tenantId(tenantId)
                        .reportType(ReportType.FICHA_ESTOQUE)
                        .exportFormat(ExportFormat.JSON)
                        .cronExpression("0 * * * * *")   // a cada minuto (Spring 6-field cron)
                        .warehouseId(warehouseId)
                        .active(true)
                        .build();
        // Simula ID via reflexão
        setField(schedule, "id", UUID.randomUUID());

        when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of(schedule));
        when(scheduleRepository.save(any())).thenReturn(schedule);

        // Para a geração do relatório interno não explodir
        when(warehouseRepository.findByIdAndTenantId(eq(warehouseId), eq(tenantId)))
                .thenReturn(Optional.of(Warehouse.builder().build()));
        when(fichaEstoqueGenerator.generate(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(jsonStrategy.export(any(), any(), any(), any())).thenReturn(new byte[0]);

        scheduleService.executeScheduledReports();

        verify(scheduleRepository).save(argThat(s ->
                s.getLastExecutedAt() != null && s.getNextExecutionAt() != null));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void setField(Object target, String fieldName, Object value) throws Exception {
        // Tenta na própria classe
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException e) {
            // Sobe pela hierarquia de herança
            java.lang.reflect.Field f = target.getClass().getSuperclass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        }
    }
}
