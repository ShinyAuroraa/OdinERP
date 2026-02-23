package com.odin.wms.report.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.domain.enums.ExportFormat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Exporta relatório em formato Excel (.xlsx) usando Apache POI.
 * Usa SXSSFWorkbook (streaming) para suportar datasets grandes sem estouro de memória.
 */
@Component
public class ExcelExportStrategy implements ReportExportStrategy {

    @Override
    public ExportFormat getFormat() {
        return ExportFormat.EXCEL;
    }

    @Override
    public byte[] export(List<?> rows, String reportTitle, String period, ObjectMapper mapper) throws IOException {
        // SXSSFWorkbook: mantém apenas 100 linhas em memória, flush automático
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet(reportTitle.length() > 31 ? "Relatório" : reportTitle);

            List<Map<String, Object>> maps;
            if (rows.isEmpty()) {
                maps = List.of();
            } else {
                maps = rows.stream()
                        .map(r -> mapper.convertValue(r, new TypeReference<Map<String, Object>>() {}))
                        .toList();
            }

            // Estilo de cabeçalho: negrito + fundo azul escuro
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<String> headers;
            if (!maps.isEmpty()) {
                headers = new ArrayList<>(maps.get(0).keySet());
            } else {
                headers = List.of();
            }

            // Linha de cabeçalho
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Dados
            int rowIdx = 1;
            for (Map<String, Object> map : maps) {
                Row dataRow = sheet.createRow(rowIdx++);
                for (int col = 0; col < headers.size(); col++) {
                    Object val = map.get(headers.get(col));
                    Cell cell = dataRow.createCell(col);
                    if (val != null) {
                        cell.setCellValue(val.toString());
                    }
                }
            }

            // Auto-filter na linha de cabeçalho
            if (!headers.isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.size() - 1));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.dispose(); // libera arquivos temporários do SXSSFWorkbook
            return baos.toByteArray();
        }
    }
}
