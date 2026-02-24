package com.odin.wms.android.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferCreateScreen(
    scannedCode: String? = null,
    onScanClick: () -> Unit,
    onConfirmClick: (sourceLocation: String, productCode: String, qty: Int, lotNumber: String?, destinationLocation: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var sourceLocation by remember { mutableStateOf("") }
    var productCode by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var lotNumber by remember { mutableStateOf("") }
    var destinationLocation by remember { mutableStateOf("") }

    // Apply scanned code to current step field
    scannedCode?.let { code ->
        when (currentStep) {
            1 -> if (sourceLocation.isBlank()) sourceLocation = code
            2 -> if (productCode.isBlank()) productCode = code
            4 -> if (destinationLocation.isBlank()) destinationLocation = code
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova Transferência") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step indicator
            StepIndicator(currentStep = currentStep, totalSteps = 4)

            Spacer(modifier = Modifier.height(8.dp))

            when (currentStep) {
                1 -> {
                    Text("Passo 1: Posição de Origem", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = sourceLocation,
                            onValueChange = { sourceLocation = it },
                            label = { Text("Posição de origem") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onScanClick) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear")
                        }
                    }
                    Button(
                        onClick = { currentStep = 2 },
                        enabled = sourceLocation.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar Origem")
                    }
                }
                2 -> {
                    Text("Passo 2: Produto", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = productCode,
                            onValueChange = { productCode = it },
                            label = { Text("Código do produto") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onScanClick) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear")
                        }
                    }
                    Button(
                        onClick = { currentStep = 3 },
                        enabled = productCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar Produto")
                    }
                    OutlinedButton(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Voltar")
                    }
                }
                3 -> {
                    Text("Passo 3: Quantidade", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Quantidade") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lotNumber,
                        onValueChange = { lotNumber = it },
                        label = { Text("Lote (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { currentStep = 4 },
                        enabled = qtyText.isNotBlank() && (qtyText.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar Quantidade")
                    }
                    OutlinedButton(
                        onClick = { currentStep = 2 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Voltar")
                    }
                }
                4 -> {
                    Text("Passo 4: Posição de Destino", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = destinationLocation,
                            onValueChange = { destinationLocation = it },
                            label = { Text("Posição de destino") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onScanClick) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear")
                        }
                    }
                    Button(
                        onClick = {
                            onConfirmClick(
                                sourceLocation,
                                productCode,
                                qtyText.toIntOrNull() ?: 0,
                                lotNumber.ifBlank { null },
                                destinationLocation
                            )
                        },
                        enabled = destinationLocation.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Revisar Transferência")
                    }
                    OutlinedButton(
                        onClick = { currentStep = 3 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Voltar")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(totalSteps) { index ->
            val step = index + 1
            val isActive = step == currentStep
            val isDone = step < currentStep
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
                color = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isDone -> Color(0xFF107C10)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small
            ) {}
        }
    }
    Text(
        text = "Passo $currentStep de $totalSteps",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
