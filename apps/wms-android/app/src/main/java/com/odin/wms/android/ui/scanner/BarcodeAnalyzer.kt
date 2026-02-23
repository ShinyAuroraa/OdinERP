package com.odin.wms.android.ui.scanner

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val onBarcodeDetected: (code: String, format: String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.let { barcode ->
                    val rawValue = barcode.rawValue ?: return@let
                    val format = formatName(barcode.format)
                    onBarcodeDetected(rawValue, format)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    override fun close() {
        scanner.close()
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_EAN_13     -> "EAN_13"
        Barcode.FORMAT_EAN_8      -> "EAN_8"
        Barcode.FORMAT_CODE_128   -> "CODE_128"
        Barcode.FORMAT_QR_CODE    -> "QR_CODE"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        else                       -> "UNKNOWN"
    }
}
