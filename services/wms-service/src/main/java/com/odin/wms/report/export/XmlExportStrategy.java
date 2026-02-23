package com.odin.wms.report.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.domain.enums.ExportFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Exporta relatório em formato XML.
 * Elemento raiz &lt;relatorio&gt; com atributos de metadados e elementos filhos por linha.
 */
@Component
public class XmlExportStrategy implements ReportExportStrategy {

    @Override
    public ExportFormat getFormat() {
        return ExportFormat.XML;
    }

    @Override
    public byte[] export(List<?> rows, String reportTitle, String period, ObjectMapper mapper) throws IOException {
        List<Map<String, Object>> maps = rows.stream()
                .map(r -> mapper.convertValue(r, new TypeReference<Map<String, Object>>() {}))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<relatorio")
          .append(" tipo=\"").append(escapeXml(reportTitle)).append("\"")
          .append(" periodo=\"").append(escapeXml(period)).append("\"")
          .append(" geradoEm=\"").append(Instant.now()).append("\"")
          .append(" totalLinhas=\"").append(maps.size()).append("\"")
          .append(">\n");

        for (Map<String, Object> row : maps) {
            sb.append("  <item>\n");
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue() != null ? escapeXml(entry.getValue().toString()) : "";
                sb.append("    <").append(key).append(">")
                  .append(val)
                  .append("</").append(key).append(">\n");
            }
            sb.append("  </item>\n");
        }

        sb.append("</relatorio>");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
