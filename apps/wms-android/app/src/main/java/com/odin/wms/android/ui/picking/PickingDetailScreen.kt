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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odin.wms.android.domain.model.PickingItem
import com.odin.wms.android.domain.model.PickingItemLocalStatus
import com.odin.wms.android.domain.model.PickingTask
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickingDetailScreen(
    taskId: String,
    viewModel: PickingViewModel = hiltViewModel(),
    onScanClick: () -> Unit,
    onConfirmItem: (String, String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(taskId) {
        viewModel.loadTaskDetail(taskId)
    }

    when (val state = uiState) {
        is PickingUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is PickingUiState.TaskDetail -> {
            PickingDetailContent(
                task = state.task,
                items = state.items,
                isOffline = state.isOffline,
                onScanClick = onScanClick,
                onConfirmItem = { itemId -> onConfirmItem(taskId, itemId) },
                onCompleteTask = { onCompleteTask(taskId) },
                onNavigateBack = onNavigateBack
            )
        }
        is PickingUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.loadTaskDetail(taskId) }) {
                    Text("Tentar novamente")
                }
            }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickingDetailContent(
    task: PickingTask,
    items: List<PickingItem>,
    isOffline: Boolean,
    onScanClick: () -> Unit,
    onConfirmItem: (String) -> Unit,
    onCompleteTask: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val allDone = items.isNotEmpty() && items.all {
        it.localStatus == PickingItemLocalStatus.PICKED ||
        it.localStatus == PickingItemLocalStatus.PICKED_OFFLINE ||
        it.localStatus == PickingItemLocalStatus.SKIPPED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tarefa ${task.taskNumber}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isOffline) {
                PickingOfflineBanner()
            }

            // Task header
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tarefa: ${task.taskNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    task.corridor?.let {
                        Text(text = "Corredor: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "${task.pickedItems}/${task.totalItems} itens pickados",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Items list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 16.dp
                )
            ) {
                items(items.sortedBy { it.position }, key = { it.id }) { item ->
                    PickingItemCard(
                        item = item,
                        onClick = { onConfirmItem(item.id) }
                    )
                }
            }

            // Complete button
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = onCompleteTask,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = allDone
                ) {
                    Text("Concluir Separação")
                }
            }
        }
    }
}

@Composable
private fun PickingItemCard(item: PickingItem, onClick: () -> Unit) {
    val today = LocalDate.now()
    val isNearExpiry = item.expiryDate?.let { it.isBefore(today.plusDays(30)) } ?: false

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productCode,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isNearExpiry) {
                        FEFOBadge()
                    }
                    ItemStatusBadge(item.localStatus)
                }
            }
            Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Corredor ${item.position.substringBefore("-")} — Posição ${item.position}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Qtd esperada: ${item.expectedQty} | Pickada: ${item.pickedQty}",
                style = MaterialTheme.typography.bodySmall
            )
            item.expiryDate?.let {
                Text(
                    text = "Validade: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNearExpiry) Color(0xFF856404) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FEFOBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFFFF3CD)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "FEFO",
                tint = Color(0xFF856404),
                modifier = Modifier.height(12.dp)
            )
            Text(
                text = "FEFO",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF856404)
            )
        }
    }
}

@Composable
private fun ItemStatusBadge(status: PickingItemLocalStatus) {
    val (label, color) = when (status) {
        PickingItemLocalStatus.PENDING        -> "Pendente" to MaterialTheme.colorScheme.primary
        PickingItemLocalStatus.PICKED         -> "Pickado" to Color(0xFF107C10)
        PickingItemLocalStatus.PICKED_OFFLINE -> "Offline" to Color(0xFFE67E22)
        PickingItemLocalStatus.SKIPPED        -> "Pulado" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
