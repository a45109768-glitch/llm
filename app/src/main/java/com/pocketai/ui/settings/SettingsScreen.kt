package com.pocketai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketai.data.preferences.RoutingMode
import com.pocketai.ui.cloud.dialogTextFieldColors
import com.pocketai.ui.theme.CyanAccent
import com.pocketai.ui.theme.EmeraldStatus
import com.pocketai.ui.theme.SlateDark700
import com.pocketai.ui.theme.SlateDark800
import com.pocketai.ui.theme.SlateDark850
import com.pocketai.ui.theme.SlateDark900
import com.pocketai.ui.theme.SlateDark950
import com.pocketai.ui.theme.VioletAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLocalModels: () -> Unit,
    onNavigateToCloudProviders: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToObsidian: () -> Unit,
    onNavigateToRobot: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    var systemPromptInput by remember { mutableStateOf(preferences.systemPrompt) }

    LaunchedEffect(preferences.systemPrompt) {
        systemPromptInput = preferences.systemPrompt
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("settings_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateDark950,
                    titleContentColor = Color.White
                ),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateDark950)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: AI & Routing Engine
            item {
                SectionHeader("AI & Model Routing", Icons.Default.Route)
            }

            item {
                Surface(
                    color = SlateDark900,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Model Routing Policy",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Controls whether queries are executed locally on-device or routed to cloud providers.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        RoutingMode.values().forEach { mode ->
                            val isSelected = preferences.routingMode == mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setRoutingMode(mode) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                    .testTag("routing_option_${mode.name}")
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setRoutingMode(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CyanAccent,
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = mode.name.replace("_", " "),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isSelected) CyanAccent else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    Text(
                                        text = when (mode) {
                                            RoutingMode.LOCAL_FIRST -> "Prefers local GGUF model; falls back to cloud if unavailable"
                                            RoutingMode.CLOUD_FIRST -> "Uses cloud API first; falls back to on-device GGUF"
                                            RoutingMode.LOCAL_ONLY -> "Strict offline privacy; never contacts remote cloud"
                                            RoutingMode.CLOUD_ONLY -> "Routes exclusively to configured cloud endpoints"
                                            RoutingMode.AUTO -> "Selects best path dynamically based on connectivity"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Navigation Links for Models
            item {
                Surface(
                    color = SlateDark900,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsNavigationRow(
                            title = "Local GGUF Models",
                            subtitle = "Download, import, load/unload on-device models",
                            icon = Icons.Default.Memory,
                            iconTint = EmeraldStatus,
                            onClick = onNavigateToLocalModels,
                            tag = "nav_local_models"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SlateDark800))
                        SettingsNavigationRow(
                            title = "Cloud AI Providers",
                            subtitle = "DeepSeek, OpenAI, Groq, Ollama & custom APIs",
                            icon = Icons.Default.Cloud,
                            iconTint = CyanAccent,
                            onClick = onNavigateToCloudProviders,
                            tag = "nav_cloud_providers"
                        )
                    }
                }
            }

            // Section 2: Memory & Knowledge Vault
            item {
                SectionHeader("Memory & Knowledge", Icons.Default.Bookmark)
            }

            item {
                Surface(
                    color = SlateDark900,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsNavigationRow(
                            title = "Personal Memory Vault",
                            subtitle = "Room-persisted facts, goals, and user identity",
                            icon = Icons.Default.Bookmark,
                            iconTint = VioletAccent,
                            onClick = onNavigateToMemory,
                            tag = "nav_memory"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SlateDark800))
                        SettingsNavigationRow(
                            title = "Obsidian Vault Integration",
                            subtitle = "SAF connection to local Markdown knowledge notes",
                            icon = Icons.Default.Description,
                            iconTint = CyanAccent,
                            onClick = onNavigateToObsidian,
                            tag = "nav_obsidian"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SlateDark800))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Inject Memories into Context",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "Automatically injects your top personal memories into assistant prompts",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                )
                            }
                            Switch(
                                checked = preferences.isMemoryEnabled,
                                onCheckedChange = { viewModel.setMemoryEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = CyanAccent
                                ),
                                modifier = Modifier.testTag("memory_injection_switch")
                            )
                        }
                    }
                }
            }

            // Section 3: Persona / System Prompt
            item {
                SectionHeader("Assistant Persona", Icons.Default.Person)
            }

            item {
                Surface(
                    color = SlateDark900,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Base System Prompt",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Instructs the AI on its personality, style, and domain behavior across all models.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = systemPromptInput,
                            onValueChange = { systemPromptInput = it },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth().testTag("system_prompt_input"),
                            colors = dialogTextFieldColors()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = { viewModel.setSystemPrompt(systemPromptInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("save_prompt_button")
                            ) {
                                Text("Save Persona", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 4: Future Robotics
            item {
                SectionHeader("Robotics & External Hardware", Icons.Default.PrecisionManufacturing)
            }

            item {
                Surface(
                    color = SlateDark900,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsNavigationRow(
                        title = "Robot Control Platform",
                        subtitle = "Server gateway, ESP32 pairing credentials, and topology",
                        icon = Icons.Default.PrecisionManufacturing,
                        iconTint = EmeraldStatus,
                        onClick = onNavigateToRobot,
                        tag = "nav_robot"
                    )
                }
            }

            // Section 5: App Info & Diagnostics
            item {
                Surface(
                    color = SlateDark850,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PocketAI Native",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Version 1.0.0 • Local-First, Model-Agnostic Core with Room & Obsidian SAF integration.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
        )
    }
}

@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SlateDark800),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(14.dp)
        )
    }
}
