package com.odin.wms.report.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.wms.domain.enums.ExportFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Exporta relatório em formato JSON.
 */
@Component
public class JsonExportStrategy implements ReportExportStrategy {

    @Override
    public ExportFormat getFormat() {
        return ExportFormat.JSON;
    }

    @Override
    public byte[] export(List<?> rows, String reportTitle, String period, ObjectMapper mapper) throws IOException {
        return mapper.writeValueAsBytes(rows);
    }
}
