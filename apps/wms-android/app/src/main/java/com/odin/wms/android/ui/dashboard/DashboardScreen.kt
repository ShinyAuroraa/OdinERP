package com.odin.wms.android.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odin.wms.android.domain.model.StockSummary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToOperations: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullState = rememberPullToRefreshState()

    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
            pullState.endRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullState.nestedScrollConnection)
    ) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is DashboardUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Tentar novamente") }
                }
            }
            is DashboardUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("dashboard_content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isOffline) {
                        item {
                            OfflineBanner(lastUpdated = state.stockSummary.lastUpdated)
                        }
                    }
                    item {
                        StockSummaryCard(summary = state.stockSummary)
                    }
                    item {
                        Text("Acesso rápido", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    item {
                        OperationCard(
                            icon = Icons.Default.LocalShipping,
                            title = "Recebimento",
                            badge = state.stockSummary.pendingReceivingCount,
                            onClick = onNavigateToOperations
                        )
                    }
                    item {
                        OperationCard(
                            icon = Icons.Default.Inventory,
                            title = "Picking",
                            badge = state.stockSummary.pendingPickingCount,
                            onClick = onNavigateToOperations
                        )
                    }
                    item {
                        OperationCard(
                            icon = Icons.Default.FactCheck,
                            title = "Inventário",
                            badge = 0,
                            onClick = onNavigateToOperations
                        )
                    }
                    item {
                        OperationCard(
                            icon = Icons.Default.SwapHoriz,
                            title = "Transferências",
                            badge = 0,
                            onClick = onNavigateToOperations
                        )
                    }
                }
            }
        }
        PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun OfflineBanner(lastUpdated: Long) {
    val formatted = remember(lastUpdated) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdated))
    }
    Surface(
        color = Color(0xFFFFF3CD),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Dados offline · atualizado às $formatted",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF856404)
        )
    }
}

@Composable
fun StockSummaryCard(summary: StockSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Resumo de Estoque", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column {
                    Text("${summary.totalAvailable}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Disponível", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun OperationCard(
    icon: ImageVector,
    title: String,
    badge: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
                Text(title, fontWeight = FontWeight.Medium)
            }
            if (badge > 0) {
                Badge { Text("$badge") }
            }
        }
    }
}
