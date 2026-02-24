package com.odin.wms.android.ui.scanner

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface ScannerUiState {
    data object Scanning : ScannerUiState
    data class CodeDetected(val code: String, val format: String) : ScannerUiState
    data object ManualInput : ScannerUiState
    data object PermissionDenied : ScannerUiState
}

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Scanning)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onBarcodeDetected(code: String, format: String) {
        _uiState.value = ScannerUiState.CodeDetected(code, format)
    }

    fun onPermissionDenied() {
        _uiState.value = ScannerUiState.PermissionDenied
    }

    fun switchToManualInput() {
        _uiState.value = ScannerUiState.ManualInput
    }

    fun resetToScanning() {
        _uiState.value = ScannerUiState.Scanning
    }
}
