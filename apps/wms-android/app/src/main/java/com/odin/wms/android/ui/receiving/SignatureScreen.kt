package com.odin.wms.android.ui.receiving

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectDragGestures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    orderId: String,
    onNavigateBack: () -> Unit,
    onSignatureComplete: () -> Unit,
    viewModel: ReceivingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Store completed strokes: list of paths, each path = list of offsets
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Total point count for validation
    val totalPoints: Int get() = strokes.sumOf { it.size } + currentStroke.size

    // Canvas bitmap reference for capturing
    var canvasWidth by remember { mutableStateOf(0) }
    var canvasHeight by remember { mutableStateOf(0) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ReceivingUiState.ConfirmSuccess -> {
                snackbarHostState.showSnackbar("Recebimento concluído!")
                onSignatureComplete()
                viewModel.resetState()
            }
            is ReceivingUiState.SyncQueued -> {
                snackbarHostState.showSnackbar("Assinatura enfileirada para sync")
                onSignatureComplete()
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
                title = { Text("Assinatura Digital") },
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
                Text(
                    text = "Enviando assinatura...",
                    modifier = Modifier.padding(top = 72.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Assine abaixo para confirmar o recebimento",
                    style = MaterialTheme.typography.bodyLarge
                )

                // Signature canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Gray)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes.add(ArrayList(currentStroke))
                                        currentStroke = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    canvasWidth = size.width.toInt()
                    canvasHeight = size.height.toInt()

                    // Draw completed strokes
                    for (stroke in strokes) {
                        stroke.zipWithNext { a, b ->
                            drawLine(
                                color = Color.Black,
                                start = a,
                                end = b,
                                strokeWidth = 4f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    // Draw current stroke
                    currentStroke.zipWithNext { a, b ->
                        drawLine(
                            color = Color.Black,
                            start = a,
                            end = b,
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                Text(
                    text = if (totalPoints > 0) "$totalPoints pontos desenhados" else "Canvas vazio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            strokes.clear()
                            currentStroke = emptyList()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Limpar")
                    }

                    Button(
                        onClick = {
                            if (totalPoints < 50) return@Button
                            // Render strokes to bitmap
                            val bitmap = Bitmap.createBitmap(
                                if (canvasWidth > 0) canvasWidth else 800,
                                if (canvasHeight > 0) canvasHeight else 400,
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                strokeWidth = 4f
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                            }
                            for (stroke in strokes) {
                                val path = android.graphics.Path()
                                stroke.forEachIndexed { index, offset ->
                                    if (index == 0) path.moveTo(offset.x, offset.y)
                                    else path.lineTo(offset.x, offset.y)
                                }
                                canvas.drawPath(path, paint)
                            }
                            viewModel.submitSignatureAndComplete(orderId, bitmap)
                        },
                        enabled = totalPoints >= 50,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirmar Assinatura")
                    }
                }

                if (totalPoints < 50 && totalPoints > 0) {
                    Text(
                        text = "Continue assinando (mínimo 50 pontos)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
