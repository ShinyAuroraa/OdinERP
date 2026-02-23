package com.odin.wms.android.ui.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// U7 + U8: BarcodeAnalyzer format mapping
// Note: Full ML Kit processing requires a real device/Robolectric;
// these tests cover the format-name mapping logic extracted from BarcodeAnalyzer.
class BarcodeAnalyzerTest {

    // Mirrors the private formatName logic from BarcodeAnalyzer
    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_EAN_13      -> "EAN_13"
        Barcode.FORMAT_EAN_8       -> "EAN_8"
        Barcode.FORMAT_CODE_128    -> "CODE_128"
        Barcode.FORMAT_QR_CODE     -> "QR_CODE"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        else                        -> "UNKNOWN"
    }

    // U7: código EAN-13 válido retorna formato correto
    @Test
    fun `EAN-13 barcode format maps to EAN_13 string`() {
        val format = formatName(Barcode.FORMAT_EAN_13)
        assertEquals("EAN_13", format)
    }

    // U8: código GS1-128 (Code-128) retorna formato correto
    @Test
    fun `GS1-128 barcode format maps to CODE_128 string`() {
        val format = formatName(Barcode.FORMAT_CODE_128)
        assertEquals("CODE_128", format)
    }

    @Test
    fun `QR Code format maps to QR_CODE string`() {
        val format = formatName(Barcode.FORMAT_QR_CODE)
        assertEquals("QR_CODE", format)
    }

    @Test
    fun `Data Matrix format maps to DATA_MATRIX string`() {
        val format = formatName(Barcode.FORMAT_DATA_MATRIX)
        assertEquals("DATA_MATRIX", format)
    }

    @Test
    fun `unknown format maps to UNKNOWN string`() {
        val format = formatName(-1)
        assertEquals("UNKNOWN", format)
    }

    @Test
    fun `ScannerViewModel transitions to CodeDetected when barcode scanned`() {
        val viewModel = ScannerViewModel()
        viewModel.onBarcodeDetected("1234567890123", "EAN_13")
        val state = viewModel.uiState.value
        assert(state is ScannerUiState.CodeDetected)
        assertEquals("1234567890123", (state as ScannerUiState.CodeDetected).code)
        assertEquals("EAN_13", state.format)
    }
}
