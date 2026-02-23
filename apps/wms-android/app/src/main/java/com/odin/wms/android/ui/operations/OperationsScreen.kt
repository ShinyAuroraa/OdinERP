package com.odin.wms.android.ui.operations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odin.wms.android.domain.model.WmsRole

private data class OperationItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val requiredRole: WmsRole = WmsRole.WMS_OPERATOR
)

private val allOperations = listOf(
    OperationItem("Recebimento", "Conferência e entrada de mercadorias",   Icons.Default.LocalShipping),
    OperationItem("Picking",     "Separação de itens para expedição",       Icons.Default.Inventory),
    OperationItem("Inventário",  "Contagem e ajuste de estoque físico",     Icons.Default.FactCheck),
    OperationItem("Transferência","Movimentação interna entre posições",    Icons.Default.SwapHoriz),
    OperationItem("Relatórios",  "Relatórios regulatórios e analytics",     Icons.Default.Assessment,
                  requiredRole = WmsRole.WMS_SUPERVISOR)
)

@Composable
fun OperationsScreen(
    onOperationClick: (String) -> Unit = {},
    viewModel: OperationsViewModel = hiltViewModel()
) {
    val userRole by viewModel.userRole.collectAsState()
    val visibleOps = allOperations.filter { op ->
        when (op.requiredRole) {
            WmsRole.WMS_OPERATOR   -> true
            WmsRole.WMS_SUPERVISOR -> userRole == WmsRole.WMS_SUPERVISOR || userRole == WmsRole.WMS_ADMIN
            WmsRole.WMS_ADMIN      -> userRole == WmsRole.WMS_ADMIN
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Operações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(visibleOps) { op ->
            Card(
                onClick = { onOperationClick(op.title) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(op.icon, contentDescription = op.title, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(op.title, fontWeight = FontWeight.Medium)
                        Text(op.description, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
