package com.odin.wms.android.ui.picking

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odin.wms.android.ui.scanner.Gs1Parser
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickingConfirmScreen(
    taskId: String,
    itemId: String,
    scannedCode: String? = null,
    expectedQty: Int = 1,
    viewModel: PickingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onConfirmSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val gs1Data = remember(scannedCode) {
        scannedCode?.let { Gs1Parser.parse(it) }
    }

    var qty by remember { mutableStateOf(expectedQty.toString()) }
    var lotNumber by remember { mutableStateOf(gs1Data?.lotNumber ?: "") }
    var position by remember { mutableStateOf(gs1Data?.sscc ?: "") }
    var serialNumber by remember { mutableStateOf(gs1Data?.serialNumber ?: "") }
    var fefoWarningShown by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PickingUiState.FEFOWarning -> {
                if (!fefoWarningShown) {
                    fefoWarningShown = true
                    snackbarHostState.showSnackbar(
                        "⚠️ FEFO: Existe lote mais antigo (${state.olderLot} / vence ${state.olderExpiry}). Confirme para continuar."
                    )
                }
            }
            is PickingUiState.ConfirmSuccess -> {
                // Haptic feedback 100ms
                triggerHaptic(context)
                snackbarHostState.showSnackbar("Item confirmado!")
                onConfirmSuccess()
            }
            is PickingUiState.SyncQueued -> {
                triggerHaptic(context)
                snackbarHostState.showSnackbar("Item enfileirado para sincronização offline.")
                onConfirmSuccess()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmar Item") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gs1Data?.gtin?.let { gtin ->
                Text(
                    text = "GTIN: $gtin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                label = { Text("Quantidade Pickada") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = lotNumber,
                onValueChange = { lotNumber = it },
                label = { Text("Número do Lote") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = position,
                onValueChange = { position = it },
                label = { Text("Posição") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serialNumber,
                onValueChange = { serialNumber = it },
                label = { Text("Número de Série (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            gs1Data?.expiryDate?.let { expiry ->
                Text(
                    text = "Validade: $expiry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Confirm button — stays enabled even during FEFOWarning (non-blocking)
            Button(
                onClick = {
                    val qtyInt = qty.toIntOrNull() ?: expectedQty
                    val expiry = gs1Data?.expiryDate
                    viewModel.confirmItemPicked(
                        taskId = taskId,
                        itemId = itemId,
                        qty = qtyInt,
                        lotNumber = lotNumber.ifBlank { null },
                        position = position,
                        serialNumber = serialNumber.ifBlank { null },
                        expiryDate = expiry
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is PickingUiState.Confirming
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text(modifier = Modifier.padding(start = 8.dp), text = "Confirmar Pick")
            }
        }
    }
}

private fun triggerHaptic(context: Context) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    } catch (e: Exception) {
        // Haptic not available on all devices/emulators — ignore
    }
}
