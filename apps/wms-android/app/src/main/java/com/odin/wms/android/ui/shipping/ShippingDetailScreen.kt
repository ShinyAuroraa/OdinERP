package com.odin.wms.android.ui.shipping

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.odin.wms.android.domain.model.ShippingOrder
import com.odin.wms.android.domain.model.ShippingPackage
import com.odin.wms.android.domain.model.ShippingPackageStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippingDetailScreen(
    orderId: String,
    viewModel: ShippingViewModel = hiltViewModel(),
    onScanClick: () -> Unit,
    onConfirmPackage: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orderId) {
        viewModel.loadOrderDetail(orderId)
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ShippingUiState.ShippingComplete -> {
                snackbarHostState.showSnackbar("Manifesto encerrado com sucesso!")
            }
            is ShippingUiState.SyncQueued -> {
                snackbarHostState.showSnackbar("Operação enfileirada para sincronização offline.")
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhe da Expedição") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanClick) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Volume")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when (val state = uiState) {
            is ShippingUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ShippingUiState.OrderDetail -> {
                ShippingDetailContent(
                    order = state.order,
                    onConfirmPackage = { packageId -> onConfirmPackage(orderId, packageId) },
                    onCloseManifest = { viewModel.completeShipping(orderId) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is ShippingUiState.ShippingComplete -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Manifesto encerrado! Ordem #${orderId}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Voltar para lista")
                    }
                }
            }
            is ShippingUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.loadOrderDetail(orderId) }) {
                        Text("Tentar novamente")
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ShippingDetailContent(
    order: ShippingOrder,
    onConfirmPackage: (String) -> Unit,
    onCloseManifest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allLoaded = order.packages.isNotEmpty() &&
        order.packages.all { it.status == ShippingPackageStatus.LOADED }

    Column(modifier = modifier.fillMaxSize()) {
        // Order header
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ordem: ${order.orderNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Transportadora: ${order.carrier}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Placa: ${order.vehiclePlate ?: "—"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${order.loadedPackages}/${order.totalPackages} volumes carregados",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Packages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 16.dp
            )
        ) {
            items(order.packages, key = { it.id }) { pkg ->
                ShippingPackageCard(
                    pkg = pkg,
                    onClick = { if (pkg.status == ShippingPackageStatus.PENDING) onConfirmPackage(pkg.id) }
                )
            }
        }

        // Fechar Manifesto — enabled only when all packages are LOADED
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = onCloseManifest,
                modifier = Modifier.fillMaxWidth(),
                enabled = allLoaded
            ) {
                Text("Fechar Manifesto")
            }
        }
    }
}

@Composable
private fun ShippingPackageCard(pkg: ShippingPackage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pkg.trackingCode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                pkg.weight?.let {
                    Text(text = "Peso: ${"%.2f".format(it)} kg", style = MaterialTheme.typography.bodySmall)
                }
            }
            PackageStatusBadge(pkg.status)
        }
    }
}

@Composable
private fun PackageStatusBadge(status: ShippingPackageStatus) {
    val (label, color) = when (status) {
        ShippingPackageStatus.PENDING -> "Pendente" to MaterialTheme.colorScheme.primary
        ShippingPackageStatus.LOADED  -> "Carregado" to Color(0xFF107C10)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
