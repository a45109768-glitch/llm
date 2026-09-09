package com.pocketai.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketai.local.LocalModelInfo
import com.pocketai.local.LocalModelStatus
import com.pocketai.ui.components.EmptyStateView
import com.pocketai.ui.components.StatusBadge
import com.pocketai.ui.settings.SettingsViewModel
import com.pocketai.ui.theme.AmberWarning
import com.pocketai.ui.theme.CoralError
import com.pocketai.ui.theme.CyberCyan
import com.pocketai.ui.theme.EmeraldSuccess
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val localModels by viewModel.localModels.collectAsState()
    val loadedModel by viewModel.loadedLocalModel.collectAsState()
    val loadingModelId by viewModel.loadingModelId.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val diagnostics = remember { viewModel.getHardwareDiagnostics() }
    val latestDiagnostics = viewModel.getLatestGenerationDiagnostics()

    var showInferenceSettings by remember { mutableStateOf(false) }

    // SAF Document Picker for .gguf files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importLocalModel(it) }
    }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissOperationMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local GGUF Models", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("local_models_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInferenceSettings = !showInferenceSettings },
                        modifier = Modifier.testTag("toggle_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Inference Settings",
                            tint = if (showInferenceSettings) CyberCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("import_model_icon_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import Model")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware Diagnostics Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HardwareDiagnosticsCard(
                    totalRamMb = diagnostics.totalRamMb,
                    availableRamMb = diagnostics.availableRamMb,
                    cores = diagnostics.processorCores,
                    maxSafeMb = diagnostics.recommendedMaxModelSizeMb
                )
            }

            // Real-Time Inference Performance Banner (if model was run)
            if (latestDiagnostics != null && latestDiagnostics.tokensGenerated > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberCyan.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = CyberCyan)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Inference Diagnostics",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${latestDiagnostics.activeModelName} • ${latestDiagnostics.tokensGenerated} tokens in ${latestDiagnostics.generationTimeMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = String.format("%.1f tok/s", latestDiagnostics.tokensPerSecond),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess
                                )
                            )
                        }
                    }
                }
            }

            // Collapsible Inference Settings
            item {
                AnimatedVisibility(visible = showInferenceSettings) {
                    InferenceSettingsCard(
                        contextLength = preferences.localContextLength,
                        maxTokens = preferences.localMaxTokens,
                        threads = preferences.localCpuThreads,
                        temperature = preferences.temperature,
                        topP = preferences.localTopP,
                        repeatPenalty = preferences.localRepeatPenalty,
                        onSave = { ctx, maxTok, th, temp, topP, rep ->
                            viewModel.updateLocalInferenceSettings(ctx, maxTok, th, temp, topP, rep)
                            showInferenceSettings = false
                        }
                    )
                }
            }

            // Import CTA Banner
            item {
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_model_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Model File (.gguf)", fontWeight = FontWeight.Bold)
                }
            }

            if (localModels.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Memory,
                        title = "No Local Models Imported",
                        description = "Download any GGUF model (e.g. Bonsai 1.7B, Qwen 2.5 0.5B, Llama 3.2 1B) to your phone storage, then tap 'Import Model File' to run 100% offline private AI inference.",
                        actionLabel = "Select GGUF File",
                        onAction = { filePicker.launch(arrayOf("*/*")) }
                    )
                }
            } else {
                item {
                    Text(
                        text = "IMPORTED MODELS (${localModels.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(localModels) { model ->
                    val isCurrentLoaded = loadedModel?.id == model.id
                    val isCurrentLoading = loadingModelId == model.id || model.status == LocalModelStatus.LOADING

                    LocalModelCard(
                        model = model,
                        isLoaded = isCurrentLoaded,
                        isLoading = isCurrentLoading,
                        onLoad = { viewModel.loadLocalModel(model.id) },
                        onUnload = { viewModel.unloadLocalModel(model.id) },
                        onDelete = { viewModel.deleteLocalModel(model.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun HardwareDiagnosticsCard(
    totalRamMb: Long,
    availableRamMb: Long,
    cores: Int,
    maxSafeMb: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Hardware Headroom",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                StatusBadge(
                    text = if (availableRamMb > 1500) "OPTIMAL" else "CONSTRAINED",
                    color = if (availableRamMb > 1500) EmeraldSuccess else AmberWarning
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(label = "Free RAM", value = "${availableRamMb}MB")
                MetricColumn(label = "Total RAM", value = "${totalRamMb}MB")
                MetricColumn(label = "CPU Cores", value = "$cores Cores")
                MetricColumn(label = "Rec. Max GGUF", value = "${maxSafeMb}MB")
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun LocalModelCard(
    model: LocalModelInfo,
    isLoaded: Boolean,
    isLoading: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) CyberCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLoaded) CyberCyan else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLoaded) EmeraldSuccess.copy(alpha = 0.2f)
                                else if (model.status == LocalModelStatus.ERROR) CoralError.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CyberCyan
                            )
                        } else {
                            Icon(
                                imageVector = if (isLoaded) Icons.Default.CheckCircle else Icons.Default.Memory,
                                contentDescription = null,
                                tint = if (isLoaded) EmeraldSuccess
                                else if (model.status == LocalModelStatus.ERROR) CoralError
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            text = model.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                StatusBadge(
                    text = when {
                        isLoading -> "LOADING..."
                        isLoaded -> "LOADED"
                        model.status == LocalModelStatus.ERROR -> "ERROR"
                        else -> "UNLOADED"
                    },
                    color = when {
                        isLoading -> AmberWarning
                        isLoaded -> EmeraldSuccess
                        model.status == LocalModelStatus.ERROR -> CoralError
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Spec chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizeMb = model.sizeBytes / (1024 * 1024)
                val sizeText = if (sizeMb > 1024) String.format("%.2f GB", sizeMb / 1024.0) else "$sizeMb MB"
                StatusBadge(text = sizeText, color = CyberCyan)
                StatusBadge(text = model.format, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!model.architecture.isNullOrBlank()) {
                    StatusBadge(text = model.architecture, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!model.quantization.isNullOrBlank()) {
                    StatusBadge(text = model.quantization, color = AmberWarning)
                }
                if (!model.parametersCount.isNullOrBlank()) {
                    StatusBadge(text = model.parametersCount, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Error display if present
            if (!model.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = model.lastError,
                    style = MaterialTheme.typography.bodySmall.copy(color = CoralError)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Model",
                        tint = CoralError
                    )
                }

                if (isLoaded) {
                    OutlinedButton(
                        onClick = onUnload,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Unload from RAM")
                    }
                } else {
                    Button(
                        onClick = onLoad,
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Loading RAM...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Load Model", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InferenceSettingsCard(
    contextLength: Int,
    maxTokens: Int,
    threads: Int,
    temperature: Float,
    topP: Float,
    repeatPenalty: Float,
    onSave: (Int, Int, Int, Float, Float, Float) -> Unit
) {
    var ctx by remember { mutableStateOf(contextLength.toFloat()) }
    var maxTok by remember { mutableStateOf(maxTokens.toFloat()) }
    var th by remember { mutableStateOf(threads.toFloat()) }
    var temp by remember { mutableStateOf(temperature) }
    var p by remember { mutableStateOf(topP) }
    var rep by remember { mutableStateOf(repeatPenalty) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Inference Parameters",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = CyberCyan)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Context Length: ${ctx.roundToInt()} tokens", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = ctx,
                onValueChange = { ctx = it },
                valueRange = 512f..8192f,
                steps = 14,
                colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
            )

            Text("Max Output Tokens: ${maxTok.roundToInt()}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = maxTok,
                onValueChange = { maxTok = it },
                valueRange = 128f..2048f,
                steps = 14,
                colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
            )

            Text("CPU Threads: ${th.roundToInt()} cores", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = th,
                onValueChange = { th = it },
                valueRange = 1f..8f,
                steps = 6,
                colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
            )

            Text(String.format("Temperature: %.2f", temp), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = temp,
                onValueChange = { temp = it },
                valueRange = 0.1f..1.5f,
                steps = 13,
                colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onSave(ctx.roundToInt(), maxTok.roundToInt(), th.roundToInt(), temp, p, rep) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Save Inference Parameters", fontWeight = FontWeight.Bold)
            }
        }
    }
}
