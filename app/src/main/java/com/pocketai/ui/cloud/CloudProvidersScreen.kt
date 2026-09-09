package com.pocketai.ui.cloud

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketai.cloud.CloudProviderConfig
import com.pocketai.cloud.CloudProviderType
import com.pocketai.core.security.CredentialStore
import com.pocketai.ui.components.EmptyStateView
import com.pocketai.ui.components.StatusBadge
import com.pocketai.ui.settings.SettingsViewModel
import com.pocketai.ui.theme.AmberWarning
import com.pocketai.ui.theme.CoralError
import com.pocketai.ui.theme.CyberCyan
import com.pocketai.ui.theme.EmeraldSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProvidersScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val cloudProviders by viewModel.cloudProviders.collectAsState()
    val appPreferences by viewModel.appPreferences.collectAsState()
    val activeProviderId = appPreferences?.selectedCloudProviderId
    val isTestingApi by viewModel.isTestingApi.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissOperationMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud AI Providers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("cloud_providers_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_provider_icon_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Provider")
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
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_provider_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Cloud AI Provider", fontWeight = FontWeight.Bold)
                }
            }

            if (cloudProviders.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Cloud,
                        title = "No Cloud Providers Configured",
                        description = "Add an API endpoint for DeepSeek, OpenAI, Groq, Ollama, or any OpenAI-compatible server. PocketAI will route requests to it when cloud mode is active.",
                        actionLabel = "Add DeepSeek / OpenAI Provider",
                        onAction = { showAddDialog = true }
                    )
                }
            } else {
                item {
                    Text(
                        text = "CONFIGURED PROVIDERS (${cloudProviders.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(cloudProviders) { provider ->
                    val isActive = provider.id == activeProviderId
                    val isTestingThis = isTestingApi == provider.id

                    CloudProviderCard(
                        provider = provider,
                        isActive = isActive,
                        isTesting = isTestingThis,
                        onTest = { viewModel.testCloudProvider(provider) },
                        onToggleEnabled = { enabled -> viewModel.toggleCloudProviderEnabled(provider, enabled) },
                        onSetActive = { viewModel.setActiveCloudProvider(provider.id) },
                        onDelete = { viewModel.deleteCloudProvider(provider.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAddDialog) {
        AddCloudProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, baseUrl, apiKey, modelName, type ->
                viewModel.addCloudProvider(name, baseUrl, apiKey, modelName, type)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CloudProviderCard(
    provider: CloudProviderConfig,
    isActive: Boolean,
    isTesting: Boolean,
    onTest: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) CyberCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) CyberCyan else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Model: ${provider.modelName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = provider.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (provider.apiKey.isNotBlank()) {
                Text(
                    text = "Key: ${CredentialStore.maskKey(provider.apiKey)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Test Result Status Bar
            if (provider.lastTestedTimestamp != null) {
                val isSuccess = provider.lastTestSuccess == true
                val badgeColor = if (isSuccess) EmeraldSuccess else CoralError
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(
                        text = if (isSuccess) "TEST PASSED" else "TEST FAILED",
                        color = badgeColor
                    )
                    Text(
                        text = provider.lastTestMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = badgeColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = CoralError
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isTesting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Test API")
                    }

                    if (!isActive) {
                        Button(
                            onClick = onSetActive,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Set Active")
                        }
                    } else {
                        StatusBadge(text = "ACTIVE", color = CyberCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun AddCloudProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String, modelName: String, type: CloudProviderType) -> Unit
) {
    var selectedPreset by remember { mutableStateOf("DeepSeek") }
    var name by remember { mutableStateOf("DeepSeek") }
    var baseUrl by remember { mutableStateOf("https://api.deepseek.com/v1") }
    var apiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("deepseek-chat") }

    fun applyPreset(preset: String) {
        selectedPreset = preset
        when (preset) {
            "DeepSeek" -> {
                name = "DeepSeek"
                baseUrl = "https://api.deepseek.com/v1"
                modelName = "deepseek-chat"
            }
            "OpenAI" -> {
                name = "OpenAI"
                baseUrl = "https://api.openai.com/v1"
                modelName = "gpt-4o-mini"
            }
            "Groq" -> {
                name = "Groq"
                baseUrl = "https://api.groq.com/openai/v1"
                modelName = "llama-3.2-3b-preview"
            }
            "Ollama" -> {
                name = "Ollama Local Server"
                baseUrl = "http://10.0.2.2:11434/v1"
                modelName = "llama3.2"
                apiKey = "ollama"
            }
            "Custom" -> {
                name = "Custom Provider"
                baseUrl = "https://api.example.com/v1"
                modelName = "custom-model"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Cloud AI Provider", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quick Presets:", style = MaterialTheme.typography.labelSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("DeepSeek", "OpenAI", "Groq", "Ollama").forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { applyPreset(preset) },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name (e.g. deepseek-chat)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, baseUrl, apiKey, modelName, CloudProviderType.OPENAI_COMPATIBLE)
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()
            ) {
                Text("Save Provider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
