package com.odin.wms.android.ui.scanner

import java.time.LocalDate

/**
 * GS1 Application Identifier parser.
 *
 * Supports:
 * - EAN-13 → GTIN-14 (prepend "0")
 * - GS1-128 with AI(01) GTIN-14, AI(10) Lot/Batch, AI(17) Expiry YYMMDD, AI(21) Serial
 * - QR Code with GS1 composite string
 * - SSCC: returns gtin = null, serial = SSCC value
 */
object Gs1Parser {

    private val AI_GTIN    = Regex("01(\\d{14})")
    private val AI_LOT     = Regex("10([^\u001D]{1,20})")
    private val AI_EXPIRY  = Regex("17(\\d{6})")
    private val AI_SERIAL  = Regex("21([^\u001D]{1,20})")
    private val AI_SSCC    = Regex("00(\\d{18})")

    data class Gs1Data(
        val gtin: String? = null,
        val lotNumber: String? = null,
        val expiryDate: LocalDate? = null,
        val serialNumber: String? = null,
        val sscc: String? = null
    )

    fun parse(rawValue: String): Gs1Data {
        if (rawValue.isBlank()) return Gs1Data()

        // EAN-13: 13 digits → prepend "0" to produce GTIN-14
        if (rawValue.length == 13 && rawValue.all { it.isDigit() }) {
            return Gs1Data(gtin = "0$rawValue")
        }

        // SSCC (00 + 18 digits = 20 chars starting with "00")
        if (rawValue.startsWith("00") && rawValue.length >= 20) {
            val ssccMatch = AI_SSCC.find(rawValue)
            if (ssccMatch != null) {
                return Gs1Data(sscc = ssccMatch.groupValues[1])
            }
        }

        // GS1-128 / QR Code composite with AIs
        val gtin   = AI_GTIN.find(rawValue)?.groupValues?.get(1)
        val lot    = AI_LOT.find(rawValue)?.groupValues?.get(1)?.trimEnd('\u001D')
        val serial = AI_SERIAL.find(rawValue)?.groupValues?.get(1)?.trimEnd('\u001D')

        val expiryStr = AI_EXPIRY.find(rawValue)?.groupValues?.get(1)
        val expiry = expiryStr?.let {
            try {
                val year  = 2000 + it.substring(0, 2).toInt()
                val month = it.substring(2, 4).toInt()
                val day   = it.substring(4, 6).toInt().let { d -> if (d == 0) 1 else d }
                LocalDate.of(year, month, day)
            } catch (e: Exception) {
                null
            }
        }

        return Gs1Data(
            gtin = gtin,
            lotNumber = lot,
            expiryDate = expiry,
            serialNumber = serial
        )
    }
}
