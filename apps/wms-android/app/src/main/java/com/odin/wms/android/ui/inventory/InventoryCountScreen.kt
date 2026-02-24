package com.odin.wms.android.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryCountScreen(
    sessionId: String,
    itemId: String,
    productCode: String = "",
    gtin: String = "",
    description: String = "",
    systemQty: Int = 0,
    position: String = "",
    scannedLotNumber: String? = null,
    viewModel: InventoryViewModel = hiltViewModel(),
    onCountSuccess: () -> Unit,
    onDoubleCountClick: (String, String) -> Unit, // sessionId, itemId
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var countedQtyText by remember { mutableStateOf(systemQty.toString()) }
    var lotNumber by remember { mutableStateOf(scannedLotNumber ?: "") }
    var showDivergenceWarning by remember { mutableStateOf(false) }
    var divergencePct by remember { mutableStateOf(0f) }
    var divergenceAbs by remember { mutableStateOf(0) }

    val countedQty = countedQtyText.toIntOrNull() ?: 0

    // Real-time divergence calculation
    LaunchedEffect(countedQtyText) {
        if (systemQty > 0) {
            val diff = abs(countedQty - systemQty)
            val pct = diff.toFloat() / systemQty.toFloat()
            divergenceAbs = diff
            divergencePct = pct * 100f
            showDivergenceWarning = pct > 0.10f
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is InventoryUiState.CountSuccess, is InventoryUiState.SyncQueued -> {
                onCountSuccess()
                viewModel.resetState()
            }
            is InventoryUiState.DivergenceWarning -> {
                snackbarHostState.showSnackbar(
                    "Divergência elevada: ${String.format("%.0f", divergencePct)}%. Confirme para registrar ou use Dupla Contagem."
                )
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmar Contagem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFFDC3545)
                )
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is InventoryUiState.Confirming -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Product info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Produto", style = MaterialTheme.typography.labelMedium)
                            Text(text = productCode, style = MaterialTheme.typography.titleMedium)
                            if (description.isNotBlank()) {
                                Text(text = description, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(text = "GTIN: $gtin", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Posição: $position", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "Qtd Sistema: $systemQty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Counted Qty field (pre-filled with systemQty)
                    OutlinedTextField(
                        value = countedQtyText,
                        onValueChange = { countedQtyText = it },
                        label = { Text("Quantidade Contada") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Divergence badge (real-time)
                    if (countedQty != systemQty && countedQtyText.isNotBlank()) {
                        val diff = countedQty - systemQty
                        val label = if (diff > 0) "+$diff unid (${String.format("%.0f", divergencePct)}%)"
                        else "$diff unid (${String.format("%.0f", divergencePct)}%)"
                        val badgeColor = if (divergencePct > 10f) Color(0xFFDC3545) else Color(0xFFFF8C00)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = badgeColor
                            )
                        }
                    }

                    // Lot number field
                    OutlinedTextField(
                        value = lotNumber,
                        onValueChange = { lotNumber = it },
                        label = { Text("Lote (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Double Count button — visible only when divergence > threshold
                    if (showDivergenceWarning) {
                        Button(
                            onClick = { onDoubleCountClick(sessionId, itemId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dupla Contagem")
                        }
                    }

                    // Confirm button — always enabled even with divergence warning
                    Button(
                        onClick = {
                            viewModel.countItem(
                                sessionId = sessionId,
                                itemId = itemId,
                                productCode = productCode,
                                countedQty = countedQty,
                                systemQty = systemQty,
                                lotNumber = lotNumber.ifBlank { null },
                                position = position
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = countedQtyText.isNotBlank() && countedQty >= 0
                    ) {
                        Text("Confirmar Contagem")
                    }
                }
            }
        }
    }
}
