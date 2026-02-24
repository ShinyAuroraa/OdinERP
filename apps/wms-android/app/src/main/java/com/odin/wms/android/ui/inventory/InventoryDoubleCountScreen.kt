package com.odin.wms.android.ui.inventory

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.odin.wms.android.domain.model.InventoryItemLocalStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDoubleCountScreen(
    sessionId: String,
    itemId: String,
    firstCountQty: Int = 0,
    firstCounterId: String = "",
    currentCounterId: String = "",
    viewModel: InventoryViewModel = hiltViewModel(),
    onDoubleCountComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var doubleCountQtyText by remember { mutableStateOf("") }
    var sameOperatorError by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is InventoryUiState.DoubleCountResult, is InventoryUiState.SyncQueued -> {
                onDoubleCountComplete()
                viewModel.resetState()
            }
            is InventoryUiState.Error -> {
                val errorState = uiState as InventoryUiState.Error
                if (errorState.message.contains("operador diferente")) {
                    sameOperatorError = true
                }
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dupla Contagem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is InventoryUiState.Confirming -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is InventoryUiState.DoubleCountResult -> {
                val result = uiState as InventoryUiState.DoubleCountResult
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (result.item.localStatus == InventoryItemLocalStatus.COUNTED_VERIFIED) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = Color(0xFF107C10).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "Contagem verificada",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF107C10)
                            )
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = Color(0xFFFFC107).copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "Item requer revisão do supervisor",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF856404)
                            )
                        }
                    }
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
                    // Header with 1st count result
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "1ª Contagem",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Quantidade: $firstCountQty",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (firstCounterId.isNotBlank()) {
                                val truncated = if (firstCounterId.length > 8)
                                    firstCounterId.take(8) + "..." else firstCounterId
                                Text(
                                    text = "Operador: $truncated",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = "2ª Contagem",
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = doubleCountQtyText,
                        onValueChange = {
                            doubleCountQtyText = it
                            sameOperatorError = false
                        },
                        label = { Text("Quantidade (2ª Contagem)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = sameOperatorError
                    )

                    if (sameOperatorError) {
                        Text(
                            text = "Dupla contagem deve ser realizada por operador diferente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val qty = doubleCountQtyText.toIntOrNull() ?: return@Button
                            viewModel.doubleCount(
                                sessionId = sessionId,
                                itemId = itemId,
                                countedQty = qty,
                                counterId = currentCounterId
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = doubleCountQtyText.isNotBlank()
                    ) {
                        Text("Confirmar 2ª Contagem")
                    }
                }
            }
        }
    }
}
