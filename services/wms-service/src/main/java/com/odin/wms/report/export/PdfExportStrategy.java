package com.odin.wms.report.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.odin.wms.domain.enums.ExportFormat;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exporta relatório em formato PDF usando OpenPDF (fork do iText 2.x, LGPL).
 * Gera tabela com cabeçalho em negrito e rodapé com paginação.
 */
@Component
public class PdfExportStrategy implements ReportExportStrategy {

    @Override
    public ExportFormat getFormat() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] export(List<?> rows, String reportTitle, String period, ObjectMapper mapper) throws IOException {
        if (rows.isEmpty()) {
            return buildEmptyPdf(reportTitle, period);
        }

        // Converte cada row para Map para extrair headers e valores
        List<Map<String, Object>> maps = rows.stream()
                .map(r -> mapper.convertValue(r, new TypeReference<Map<String, Object>>() {}))
                .toList();

        List<String> headers = new ArrayList<>(maps.get(0).keySet());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate());

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PdfPageNumberEvent(reportTitle));
            document.open();

            // Metadados
            document.addTitle(reportTitle);
            document.addCreationDate();

            // Cabeçalho do relatório
            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font subFont   = new Font(Font.HELVETICA, 10, Font.NORMAL);
            document.add(new Paragraph(reportTitle, titleFont));
            document.add(new Paragraph("Período: " + period, subFont));
            document.add(new Paragraph("Gerado em: " + Instant.now(), subFont));
            document.add(Chunk.NEWLINE);

            // Tabela
            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);

            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(30, 60, 120));
                cell.setPadding(5);
                table.addCell(cell);
            }

            Font cellFont = new Font(Font.HELVETICA, 8);
            for (Map<String, Object> row : maps) {
                for (String h : headers) {
                    Object val = row.get(h);
                    PdfPCell cell = new PdfPCell(new Phrase(val != null ? val.toString() : "", cellFont));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }

            document.add(table);
        } finally {
            document.close();
        }

        return baos.toByteArray();
    }

    private byte[] buildEmptyPdf(String title, String period) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, baos);
            document.open();
            document.add(new Paragraph(title + " — Nenhum dado encontrado para o período: " + period));
        } finally {
            document.close();
        }
        return baos.toByteArray();
    }

    /** Adiciona número de página no rodapé. */
    private static class PdfPageNumberEvent extends PdfPageEventHelper {
        private final String reportTitle;

        PdfPageNumberEvent(String reportTitle) {
            this.reportTitle = reportTitle;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font footer = new Font(Font.HELVETICA, 8);
            Phrase phrase = new Phrase("Página " + writer.getPageNumber(), footer);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, phrase,
                    document.right(), document.bottom() - 10, 0);
        }
    }
}
