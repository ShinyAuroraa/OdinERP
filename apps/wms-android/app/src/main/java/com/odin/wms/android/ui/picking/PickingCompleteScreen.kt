package com.odin.wms.android.ui.picking

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odin.wms.android.domain.model.PickingItem
import com.odin.wms.android.domain.model.PickingItemLocalStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickingCompleteScreen(
    taskId: String,
    viewModel: PickingViewModel = hiltViewModel(),
    onBackToList: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is PickingUiState.ConfirmSuccess -> {
                snackbarHostState.showSnackbar("Separação concluída! Tarefa #${taskId}")
            }
            is PickingUiState.SyncQueued -> {
                snackbarHostState.showSnackbar("Conclusão enfileirada para sincronização offline.")
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Concluir Separação") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is PickingUiState.Loading, is PickingUiState.Confirming -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is PickingUiState.TaskDetail -> {
                val items = state.items
                val pickedItems = items.count {
                    it.localStatus == PickingItemLocalStatus.PICKED ||
                    it.localStatus == PickingItemLocalStatus.PICKED_OFFLINE
                }
                val skippedItems = items.count { it.localStatus == PickingItemLocalStatus.SKIPPED }
                val hasPending = items.any { it.localStatus == PickingItemLocalStatus.PENDING }

                PickingCompleteSummary(
                    task = state.task,
                    pickedCount = pickedItems,
                    skippedCount = skippedItems,
                    hasPending = hasPending,
                    onConfirmComplete = { viewModel.completeTask(taskId) },
                    onBackToList = onBackToList,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is PickingUiState.ConfirmSuccess, is PickingUiState.SyncQueued -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.height(64.dp),
                        tint = Color(0xFF107C10)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Separação concluída! Tarefa #${taskId}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBackToList, modifier = Modifier.fillMaxWidth()) {
                        Text("Voltar para lista")
                    }
                }
            }
            else -> {
                // Default: show simple layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Carregando dados da tarefa...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.completeTask(taskId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar Conclusão")
                    }
                }
            }
        }
    }
}

@Composable
private fun PickingCompleteSummary(
    task: com.odin.wms.android.domain.model.PickingTask,
    pickedCount: Int,
    skippedCount: Int,
    hasPending: Boolean,
    onConfirmComplete: () -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Resumo da Separação",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Itens pickados:")
                    Text("$pickedCount", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Itens pulados:")
                    Text("$skippedCount", fontWeight = FontWeight.Bold)
                }
                task.corridor?.let { corridor ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Corredor:")
                        Text(corridor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Confirmar Conclusão — disabled if any item is PENDING
        Button(
            onClick = onConfirmComplete,
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasPending
        ) {
            Text("Confirmar Conclusão")
        }

        if (hasPending) {
            Text(
                text = "Existem itens pendentes. Conclua ou pule todos os itens antes de finalizar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
