package com.odin.wms.android.ui.receiving

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.odin.wms.android.domain.model.DivergenceReport
import com.odin.wms.android.domain.model.DivergenceType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DivergenceScreen(
    orderId: String,
    itemId: String,
    onNavigateBack: () -> Unit,
    onDivergenceReported: () -> Unit,
    viewModel: ReceivingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var selectedType by remember { mutableStateOf(DivergenceType.SHORTAGE) }
    var actualQty by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var notesError by remember { mutableStateOf(false) }
    val capturedPhotoUris = remember { mutableStateListOf<Uri>() }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                if (capturedPhotoUris.size < 3) {
                    capturedPhotoUris.add(uri)
                }
            }
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ReceivingUiState.ConfirmSuccess -> {
                snackbarHostState.showSnackbar("Divergência registrada!")
                onDivergenceReported()
                viewModel.resetState()
            }
            is ReceivingUiState.SyncQueued -> {
                snackbarHostState.showSnackbar("Divergência enfileirada para sync")
                onDivergenceReported()
                viewModel.resetState()
            }
            is ReceivingUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as ReceivingUiState.Error).message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrar Divergência") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState is ReceivingUiState.Confirming) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tipo de divergência", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DivergenceType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                if (selectedType == DivergenceType.SHORTAGE || selectedType == DivergenceType.EXCESS) {
                    OutlinedTextField(
                        value = actualQty,
                        onValueChange = { actualQty = it },
                        label = { Text("Quantidade real recebida") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedType == DivergenceType.DAMAGE || selectedType == DivergenceType.WRONG_PRODUCT) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            notesError = false
                        },
                        label = { Text("Observação (mínimo 10 caracteres)") },
                        isError = notesError,
                        supportingText = if (notesError) {
                            { Text("Observação é obrigatória (mínimo 10 caracteres)") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }

                // Photo section
                Text("Fotos (máx. 3)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    capturedPhotoUris.forEachIndexed { index, uri ->
                        Box {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "Foto $index",
                                modifier = Modifier.size(80.dp),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { capturedPhotoUris.removeAt(index) },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remover foto", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (capturedPhotoUris.size < 3) {
                        IconButton(
                            onClick = {
                                val photoFile = File(context.filesDir, "temp_photo_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                                currentPhotoUri = uri
                                cameraLauncher.launch(uri)
                            }
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Adicionar foto", modifier = Modifier.size(40.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val requiresNotes = selectedType == DivergenceType.DAMAGE || selectedType == DivergenceType.WRONG_PRODUCT
                        if (requiresNotes && notes.length < 10) {
                            notesError = true
                            return@Button
                        }

                        // Convert photo URIs to Base64
                        val photoBase64List = capturedPhotoUris.mapNotNull { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val bytes = stream.readBytes()
                                    android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }

                        val report = DivergenceReport(
                            itemId = itemId,
                            type = selectedType,
                            actualQty = actualQty.toIntOrNull() ?: 0,
                            notes = notes,
                            photoBase64List = photoBase64List
                        )
                        viewModel.reportDivergence(orderId, itemId, report)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirmar Divergência")
                }
            }
        }
    }
}
