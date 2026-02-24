package com.odin.wms.android.ui.receiving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.odin.wms.android.domain.model.ReceivingItem
import com.odin.wms.android.domain.model.ReceivingItemStatus
import com.odin.wms.android.domain.model.ReceivingOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivingDetailScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    onScanClick: (String) -> Unit,
    onConfirmItem: (String, String) -> Unit,
    onDivergenceClick: (String, String) -> Unit,
    onFinalizeClick: (String) -> Unit,
    viewModel: ReceivingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(orderId) {
        viewModel.loadOrderDetail(orderId)
    }

    when (val state = uiState) {
        is ReceivingUiState.LoadingDetail -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ReceivingUiState.DetailLoaded -> {
            ReceivingDetailContent(
                order = state.order,
                onNavigateBack = onNavigateBack,
                onScanClick = { onScanClick(orderId) },
                onConfirmItem = { itemId -> onConfirmItem(orderId, itemId) },
                onDivergenceClick = { itemId -> onDivergenceClick(orderId, itemId) },
                onFinalizeClick = { onFinalizeClick(orderId) }
            )
        }
        is ReceivingUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadOrderDetail(orderId) }) {
                        Text("Tentar novamente")
                    }
                }
            }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceivingDetailContent(
    order: ReceivingOrder,
    onNavigateBack: () -> Unit,
    onScanClick: () -> Unit,
    onConfirmItem: (String) -> Unit,
    onDivergenceClick: (String) -> Unit,
    onFinalizeClick: () -> Unit
) {
    val allItemsDone = order.items.none { it.localStatus == ReceivingItemStatus.PENDING }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ordem ${order.orderNumber}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear item")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OrderHeaderCard(order)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Itens (${order.items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(order.items, key = { it.id }) { item ->
                ReceivingItemCard(
                    item = item,
                    onConfirmClick = { onConfirmItem(item.id) },
                    onDivergenceClick = { onDivergenceClick(item.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onFinalizeClick,
                    enabled = allItemsDone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finalizar Recebimento")
                }
            }
        }
    }
}

@Composable
private fun OrderHeaderCard(order: ReceivingOrder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Fornecedor: ${order.supplier}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Data esperada: ${order.expectedDate}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${order.confirmedItems}/${order.totalItems} itens confirmados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReceivingItemCard(
    item: ReceivingItem,
    onConfirmClick: () -> Unit,
    onDivergenceClick: () -> Unit
) {
    val statusColor = when (item.localStatus) {
        ReceivingItemStatus.CONFIRMED, ReceivingItemStatus.CONFIRMED_OFFLINE -> Color(0xFF107C10)
        ReceivingItemStatus.DIVERGENT -> Color(0xFFE17A00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "GTIN: ${item.gtin}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ItemStatusIcon(item.localStatus, statusColor)
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Esperado: ${item.expectedQty}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Confirmado: ${item.confirmedQty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            if (item.localStatus == ReceivingItemStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirmClick, modifier = Modifier.weight(1f)) {
                        Text("Scan")
                    }
                    OutlinedButton(onClick = onDivergenceClick, modifier = Modifier.weight(1f)) {
                        Text("Divergência")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemStatusIcon(status: ReceivingItemStatus, color: Color) {
    when (status) {
        ReceivingItemStatus.CONFIRMED, ReceivingItemStatus.CONFIRMED_OFFLINE ->
            Icon(Icons.Default.CheckCircle, contentDescription = "Confirmado", tint = color, modifier = Modifier.size(24.dp))
        ReceivingItemStatus.DIVERGENT ->
            Icon(Icons.Default.Warning, contentDescription = "Divergente", tint = color, modifier = Modifier.size(24.dp))
        else -> Unit
    }
}
