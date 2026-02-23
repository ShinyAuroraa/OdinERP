package com.odin.wms.traceability;

import com.odin.wms.dto.request.GS1ParseRequest;
import com.odin.wms.dto.response.GS1GeneratedResponse;
import com.odin.wms.dto.response.GS1ParsedResponse;
import com.odin.wms.exception.BusinessException;
import com.odin.wms.gs1.GS1Generator;
import com.odin.wms.gs1.GS1Parser;
import com.odin.wms.gs1.GS1Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para parsing e geração de códigos GS1.
 * AC5 (parse) e AC6 (generate).
 *
 * GTIN-14 válido usado nos testes: 07891234567895
 * Cálculo do check digit para 0789123456789:
 *   posições ímpares (×3) da direita: 9×3 + 7×3 + 5×3 + 3×3 + 1×3 + 8×3 + 0×3 = 99
 *   posições pares (×1)  da direita: 8×1 + 6×1 + 4×1 + 2×1 + 9×1 + 7×1 = 36
 *   soma = 135 → check = (10 - 135%10) % 10 = 5
 */
class GS1ServiceTest {

    private static final String VALID_GTIN_14 = "07891234567895";
    private static final String VALID_EAN_13  = "7891234567895";

    private GS1Service gs1Service;

    @BeforeEach
    void setUp() {
        GS1Generator generator = new GS1Generator();
        ReflectionTestUtils.setField(generator, "companyPrefix", "0789123");
        gs1Service = new GS1Service(new GS1Parser(), generator);
    }

    // -------------------------------------------------------------------------
    // AC5 — parse
    // -------------------------------------------------------------------------

    @Test
    void parse_validEan13_returnsGtinOnly() {
        GS1ParseRequest req = new GS1ParseRequest(VALID_EAN_13, "EAN13");
        GS1ParsedResponse resp = gs1Service.parse(req);

        assertThat(resp.format()).isEqualTo("EAN13");
        // parseEan13() converte EAN-13 → GTIN-14 com leading zero
        assertThat(resp.gtin()).isEqualTo(VALID_GTIN_14);
        assertThat(resp.lotNumber()).isNull();
        assertThat(resp.serialNumber()).isNull();
        assertThat(resp.expiryDate()).isNull();
    }

    @Test
    void parse_validGs1128WithAllAIs_returnsAllFields() {
        String barcode = "(01)07891234567895(10)LOTE-01(17)261231(21)SER-001";
        GS1ParseRequest req = new GS1ParseRequest(barcode, "GS1_128");
        GS1ParsedResponse resp = gs1Service.parse(req);

        assertThat(resp.gtin()).isEqualTo(VALID_GTIN_14);
        assertThat(resp.lotNumber()).isEqualTo("LOTE-01");
        assertThat(resp.serialNumber()).isEqualTo("SER-001");
        // YYMMDD 261231 → 2026-12-31
        assertThat(resp.expiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void parse_unknownAI_throwsBusinessException() {
        // AI (99) não é reconhecido pelos KNOWN_AIs
        GS1ParseRequest req = new GS1ParseRequest("(99)12345678901234", "GS1_128");

        assertThatThrownBy(() -> gs1Service.parse(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // AC6 — generate
    // -------------------------------------------------------------------------

    @Test
    void generate_validGtinWithAllParams_returnsAllCodes() {
        GS1GeneratedResponse resp = gs1Service.generate(
                VALID_GTIN_14, "LOTE-01", "SER-001", LocalDate.of(2026, 12, 31));

        assertThat(resp.ean13()).isEqualTo(VALID_EAN_13);
        assertThat(resp.gs1128()).contains("(01)" + VALID_GTIN_14);
        assertThat(resp.gs1128()).contains("(10)LOTE-01");
        assertThat(resp.gs1128()).contains("(17)261231");
        assertThat(resp.gs1128()).contains("(21)SER-001");
        assertThat(resp.sscc()).isNotNull();
        assertThat(resp.humanReadable()).contains("LOTE");
    }

    @Test
    void generate_invalidGtinCheckDigit_throwsBusinessException() {
        // 07891234567890: check digit correto seria 5, não 0
        assertThatThrownBy(() -> gs1Service.generate("07891234567890", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("check");
    }
}
