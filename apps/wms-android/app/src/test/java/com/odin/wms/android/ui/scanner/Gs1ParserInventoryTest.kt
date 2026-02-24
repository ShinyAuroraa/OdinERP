package com.odin.wms.android.ui.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * U1 — Gs1Parser GTIN-14 + AI(10) lote for inventory counting
 */
class Gs1ParserInventoryTest {

    // U1: parse GTIN-14 (AI 01) + lot (AI 10) in inventory position code
    @Test
    fun `U1 parse GTIN-14 AI-01 plus lot AI-10 returns correct gtin and lotNumber`() {
        // GS1-128 inventory code: GTIN-14 + lot number
        val code = "0112345678901231" + "10LOTEINV001\u001D"
        val result = Gs1Parser.parse(code)

        assertNotNull(result.gtin, "GTIN should be populated for AI(01) barcode")
        assertEquals("12345678901231", result.gtin)
        assertNotNull(result.lotNumber, "Lot number should be populated for AI(10) barcode")
        assertEquals("LOTEINV001", result.lotNumber)
    }

    @Test
    fun `parse GTIN-14 with lot and expiry returns all fields`() {
        // Inventory barcode: GTIN + lot + expiry
        val code = "0112345678901231" + "10LOTE-2026\u001D" + "17261231"
        val result = Gs1Parser.parse(code)

        assertEquals("12345678901231", result.gtin)
        assertEquals("LOTE-2026", result.lotNumber)
        assertNotNull(result.expiryDate)
        assertEquals(2026, result.expiryDate?.year)
        assertEquals(12, result.expiryDate?.monthValue)
    }

    @Test
    fun `parse GTIN-14 only returns gtin with no lot`() {
        val code = "0114567890123456"
        val result = Gs1Parser.parse(code)

        assertNotNull(result.gtin)
        assertEquals("14567890123456", result.gtin)
        assertNull(result.lotNumber, "Lot should be null when AI(10) not present")
    }
}
