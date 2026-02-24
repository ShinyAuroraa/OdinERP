package com.odin.wms.android.ui.receiving

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odin.wms.android.ui.scanner.Gs1Parser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivingConfirmScreen(
    orderId: String,
    itemId: String,
    scannedCode: String? = null,
    expectedQty: Int = 1,
    onNavigateBack: () -> Unit,
    onConfirmSuccess: () -> Unit,
    viewModel: ReceivingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Parse GS1 if code provided
    val gs1Data = remember(scannedCode) {
        scannedCode?.let { Gs1Parser.parse(it) } ?: Gs1Parser.Gs1Data()
    }

    var quantity by remember { mutableStateOf(expectedQty.toString()) }
    var lotNumber by remember { mutableStateOf(gs1Data.lotNumber ?: "") }
    var expiryDate by remember { mutableStateOf(gs1Data.expiryDate?.toString() ?: "") }
    var serialNumber by remember { mutableStateOf(gs1Data.serialNumber ?: "") }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ReceivingUiState.ConfirmSuccess -> {
                vibrateDevice(context)
                snackbarHostState.showSnackbar("Item confirmado!")
                onConfirmSuccess()
                viewModel.resetState()
            }
            is ReceivingUiState.SyncQueued -> {
                snackbarHostState.showSnackbar("Sem conexão — confirmação enfileirada para sync")
                onConfirmSuccess()
                viewModel.resetState()
            }
            is ReceivingUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as ReceivingUiState.Error).message)
                viewModel.resetState()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        if (uiState is ReceivingUiState.Confirming) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (gs1Data.gtin != null) {
                    Text(
                        text = "GTIN: ${gs1Data.gtin}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantidade recebida") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lotNumber,
                    onValueChange = { lotNumber = it },
                    label = { Text("Número do lote") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    label = { Text("Data de validade (AAAA-MM-DD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = { Text("Número de série (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val qty = quantity.toIntOrNull() ?: 0
                        viewModel.confirmItem(
                            orderId = orderId,
                            itemId = itemId,
                            quantity = qty,
                            lotNumber = lotNumber.ifBlank { null },
                            expiryDate = expiryDate.ifBlank { null },
                            serialNumber = serialNumber.ifBlank { null }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = quantity.toIntOrNull() != null && quantity.toIntOrNull()!! > 0
                ) {
                    Text("Confirmar")
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun vibrateDevice(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(100)
    }
}
