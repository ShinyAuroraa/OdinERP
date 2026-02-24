package com.odin.wms.android.ui.shipping

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippingConfirmScreen(
    orderId: String,
    packageId: String,
    scannedCode: String? = null,
    initialVehiclePlate: String? = null,
    viewModel: ShippingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onConfirmSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var trackingCode by remember { mutableStateOf(scannedCode ?: "") }
    var vehiclePlate by remember { mutableStateOf(initialVehiclePlate ?: "") }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ShippingUiState.PackageLoaded -> {
                triggerShippingHaptic(context)
                snackbarHostState.showSnackbar("Volume carregado!")
                onConfirmSuccess()
            }
            is ShippingUiState.SyncQueued -> {
                triggerShippingHaptic(context)
                snackbarHostState.showSnackbar("Operação enfileirada para sincronização offline.")
                onConfirmSuccess()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmar Carregamento") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Confirmar Carregamento de Volume",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = trackingCode,
                onValueChange = { trackingCode = it },
                label = { Text("Código de Rastreio") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vehiclePlate,
                onValueChange = { vehiclePlate = it },
                label = { Text("Placa do Veículo") },
                placeholder = { Text("Ex: ABC-1234") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.loadPackage(
                        orderId = orderId,
                        packageId = packageId,
                        trackingCode = trackingCode,
                        vehiclePlate = vehiclePlate.ifBlank { null }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = trackingCode.isNotBlank() && uiState !is ShippingUiState.Loading
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text(modifier = Modifier.padding(start = 8.dp), text = "Confirmar Carregamento")
            }
        }
    }
}

private fun triggerShippingHaptic(context: Context) {
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
        // Haptic not available on all devices — ignore
    }
}
