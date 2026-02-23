package com.odin.wms.report.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.domain.enums.ExportFormat;

import java.io.IOException;
import java.util.List;

/**
 * Strategy pattern para exportação de relatórios em diferentes formatos.
 * Cada implementação converte a lista de rows para o formato correspondente.
 */
public interface ReportExportStrategy {

    ExportFormat getFormat();

    /**
     * Exporta a lista de rows para bytes no formato correspondente.
     *
     * @param rows        lista de objetos (records/DTOs) a exportar
     * @param reportTitle título do relatório (para cabeçalho em PDF/Excel)
     * @param period      período do relatório (ex: "2026-01-01 a 2026-01-31")
     * @param mapper      ObjectMapper para serialização JSON/conversão
     * @return bytes do arquivo no formato correspondente
     */
    byte[] export(List<?> rows, String reportTitle, String period, ObjectMapper mapper) throws IOException;
}
