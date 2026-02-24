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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySubmitScreen(
    sessionId: String,
    sessionNumber: String = "",
    items: List<InventoryItem> = emptyList(),
    viewModel: InventoryViewModel = hiltViewModel(),
    onSubmitSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (uiState) {
            is InventoryUiState.SubmitSuccess, is InventoryUiState.SyncQueued -> {
                // Stay on screen to show success state
            }
            else -> Unit
        }
    }

    val pendingCount = items.count {
        it.localStatus == InventoryItemLocalStatus.PENDING ||
            it.localStatus == InventoryItemLocalStatus.OFFLINE_COUNTED
    }
    val countedCount = items.count { it.localStatus == InventoryItemLocalStatus.COUNTED }
    val verifiedCount = items.count { it.localStatus == InventoryItemLocalStatus.COUNTED_VERIFIED }
    val needsReviewCount = items.count { it.localStatus == InventoryItemLocalStatus.NEEDS_REVIEW }
    val canSubmit = pendingCount == 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submeter Inventário") },
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
            is InventoryUiState.SubmitSuccess -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF107C10),
                        modifier = Modifier.height(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Inventário submetido!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF107C10)
                    )
                    if (sessionNumber.isNotBlank()) {
                        Text(
                            text = "Sessão #$sessionNumber",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (needsReviewCount > 0) {
                        Text(
                            text = "$needsReviewCount item(s) com divergência registrada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF856404)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onSubmitSuccess) {
                        Text("Voltar para lista")
                    }
                }
            }
            is InventoryUiState.SyncQueued -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Submissão pendente de sincronização",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF856404)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onSubmitSuccess) {
                        Text("Voltar para lista")
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
                    Text(
                        text = "Resumo da Sessão",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SummaryRow("Total de itens", items.size.toString())
                            SummaryRow("Sem divergência", countedCount.toString())
                            SummaryRow("Verificados (dupla contagem)", verifiedCount.toString())
                            SummaryRow("Aguardando revisão", needsReviewCount.toString(), Color(0xFF856404))
                            if (pendingCount > 0) {
                                SummaryRow("Pendentes (não contados)", pendingCount.toString(), Color(0xFFDC3545))
                            }
                        }
                    }

                    if (pendingCount > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFDC3545).copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "Existem $pendingCount item(s) ainda não contados. Complete a contagem antes de submeter.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFDC3545)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { viewModel.submitSession(sessionId) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar Submissão")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valueColor != androidx.compose.ui.graphics.Color.Unspecified) valueColor
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
