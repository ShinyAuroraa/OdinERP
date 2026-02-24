package com.odin.wms.android.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.odin.wms.android.domain.model.InventoryItem
import com.odin.wms.android.domain.model.InventoryItemLocalStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryCountingListScreen(
    sessionId: String,
    viewModel: InventoryViewModel = hiltViewModel(),
    onItemClick: (String, String) -> Unit, // sessionId, itemId
    onScanClick: () -> Unit,
    onSubmitClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadCountingList(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista de Contagem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is InventoryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is InventoryUiState.CountingListLoaded -> {
                    if (state.isOffline) {
                        InventoryOfflineBanner()
                    }

                    val allCounted = state.items.none {
                        it.localStatus == InventoryItemLocalStatus.PENDING ||
                            it.localStatus == InventoryItemLocalStatus.OFFLINE_COUNTED
                    }

                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = { viewModel.loadCountingList(sessionId) },
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(state.items.sortedBy { it.position }, key = { it.id }) { item ->
                                InventoryItemCard(
                                    item = item,
                                    onClick = { onItemClick(sessionId, item.id) }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onSubmitClick(sessionId) },
                        enabled = allCounted && state.items.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Submeter Inventário")
                    }
                }
                is InventoryUiState.Error -> {
                    InventoryErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadCountingList(sessionId) }
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun InventoryItemCard(item: InventoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productCode,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pos: ${item.position}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Sistema: ${item.systemQty}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val countedText = item.countedQty?.toString() ?: "—"
                    Text(
                        text = "Contado: $countedText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.countedQty != null && item.countedQty != item.systemQty)
                            Color(0xFFDC3545) else MaterialTheme.colorScheme.onSurface
                    )
                    // Divergence badge
                    if (item.countedQty != null && item.countedQty != item.systemQty) {
                        DivergenceBadge(item.systemQty, item.countedQty)
                    }
                    // NEEDS_REVIEW badge
                    if (item.localStatus == InventoryItemLocalStatus.NEEDS_REVIEW) {
                        NeedsReviewBadge()
                    }
                }
            }
        }
    }
}

@Composable
fun DivergenceBadge(systemQty: Int, countedQty: Int) {
    val diff = countedQty - systemQty
    val label = if (diff > 0) "+$diff" else "$diff"
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFDC3545).copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFDC3545)
        )
    }
}

@Composable
fun NeedsReviewBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFFFC107).copy(alpha = 0.15f)
    ) {
        Text(
            text = "Revisão necessária",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF856404)
        )
    }
}
