package com.odin.wms.android.ui.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * U1 — Gs1Parser SSCC parse for picking position identification
 */
class Gs1ParserPickingTest {

    // U1: SSCC (AI 00) returns positionCode in sscc field (used for position identification in picking)
    @Test
    fun `U1 parse SSCC AI-00 returns positionCode in sscc field`() {
        // SSCC: AI(00) + 18 digits
        val ssccCode = "00987654321098765432"

        val result = Gs1Parser.parse(ssccCode)

        assertNotNull(result.sscc, "SSCC field should be populated for AI(00) barcode")
        assertEquals("987654321098765432", result.sscc)
        assertNull(result.gtin, "GTIN should be null for SSCC-only barcode")
        assertNull(result.lotNumber, "Lot number should be null for SSCC-only barcode")
        assertNull(result.expiryDate, "Expiry date should be null for SSCC-only barcode")
    }

    @Test
    fun `parse SSCC with minimal 20-char input returns sscc`() {
        // Exactly 20 characters: "00" + 18 digits
        val sscc = "00" + "1".repeat(18)
        val result = Gs1Parser.parse(sscc)
        assertNotNull(result.sscc)
        assertEquals("1".repeat(18), result.sscc)
    }

    @Test
    fun `parse GS1-128 with GTIN and expiry for picking confirmation`() {
        // Picking confirmation barcode: GTIN + lot + expiry
        val code = "0112345678901234" + "10LOT-PICK\u001D" + "17260630"
        val result = Gs1Parser.parse(code)

        assertEquals("12345678901234", result.gtin)
        assertEquals("LOT-PICK", result.lotNumber)
        assertNotNull(result.expiryDate)
        assertEquals(2026, result.expiryDate?.year)
        assertEquals(6, result.expiryDate?.monthValue)
    }
}
