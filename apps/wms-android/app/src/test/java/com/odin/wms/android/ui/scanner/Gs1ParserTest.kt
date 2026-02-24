package com.odin.wms.android.ui.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * U1, U2, U3 — Gs1Parser unit tests
 */
class Gs1ParserTest {

    // U1: EAN-13 → GTIN-14 with correct "0" prefix
    @Test
    fun `U1 parse EAN-13 returns GTIN-14 with leading zero`() {
        val ean13 = "7891234567890"   // 13 digits
        val result = Gs1Parser.parse(ean13)

        assertEquals("07891234567890", result.gtin, "GTIN-14 should be EAN-13 prepended with '0'")
        assertNull(result.lotNumber)
        assertNull(result.expiryDate)
        assertNull(result.serialNumber)
    }

    // U2: GS1-128 with AI(01)+AI(10)+AI(17) extracts gtin, lotNumber and expiryDate
    @Test
    fun `U2 parse GS1-128 with multiple AIs extracts GTIN lot and expiry`() {
        // GS1-128: AI(01)=GTIN-14, AI(10)=LOT123, AI(17)=260101 (2026-01-01)
        val gs1Code = "0112345678901234" +
            "10LOT123" + "\u001D" +   // AI(10) lot with GS1 separator
            "17260101"                  // AI(17) expiry

        val result = Gs1Parser.parse(gs1Code)

        assertEquals("12345678901234", result.gtin)
        assertEquals("LOT123", result.lotNumber)
        assertNotNull(result.expiryDate)
        assertEquals(LocalDate.of(2026, 1, 1), result.expiryDate)
        assertNull(result.serialNumber)
    }

    // U3: Invalid string (no AIs) → returns Gs1Data with all nulls
    @Test
    fun `U3 parse invalid string returns empty Gs1Data`() {
        val invalid = "NOTAGS_NO_GS1"

        val result = Gs1Parser.parse(invalid)

        assertNull(result.gtin, "GTIN should be null for invalid code")
        assertNull(result.lotNumber)
        assertNull(result.expiryDate)
        assertNull(result.serialNumber)
        assertNull(result.sscc)
    }

    @Test
    fun `parse blank string returns empty Gs1Data`() {
        val result = Gs1Parser.parse("")
        assertNull(result.gtin)
        assertNull(result.lotNumber)
    }

    @Test
    fun `parse GS1-128 with AI(21) serial number`() {
        val gs1Code = "0112345678901234" + "21SN987654"
        val result = Gs1Parser.parse(gs1Code)

        assertEquals("12345678901234", result.gtin)
        assertEquals("SN987654", result.serialNumber)
    }

    @Test
    fun `parse SSCC returns sscc field`() {
        // AI(00) + 18 digits
        val ssccCode = "00123456789012345678"
        val result = Gs1Parser.parse(ssccCode)

        assertNotNull(result.sscc)
        assertEquals("123456789012345678", result.sscc)
        assertNull(result.gtin)
    }

    @Test
    fun `parse EAN-13 with exactly 13 digits that are all numeric`() {
        val ean = "1234567890128"
        val result = Gs1Parser.parse(ean)
        assertEquals("01234567890128", result.gtin)
    }
}
