package com.pocketai.ui.obsidian

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketai.obsidian.ObsidianConstants
import com.pocketai.ui.cloud.dialogTextFieldColors
import com.pocketai.ui.settings.SettingsViewModel
import com.pocketai.ui.theme.CyanAccent
import com.pocketai.ui.theme.EmeraldStatus
import com.pocketai.ui.theme.RoseStatus
import com.pocketai.ui.theme.SlateDark700
import com.pocketai.ui.theme.SlateDark800
import com.pocketai.ui.theme.SlateDark850
import com.pocketai.ui.theme.SlateDark900
import com.pocketai.ui.theme.SlateDark950
import com.pocketai.ui.theme.VioletAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsidianScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val vaultStatus by viewModel.vaultStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var quickNoteTitle by remember { mutableStateOf("") }
    var quickNoteContent by remember { mutableStateOf("") }

    val vaultPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.connectVault(treeUri)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("obsidian_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateDark950,
                    titleContentColor = Color.White
                ),
                title = { Text("Obsidian Knowledge Vault") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Vault Connection Status Card
            item {
                Surface(
                    color = SlateDark900,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("vault_status_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (vaultStatus.isConnected) EmeraldStatus else RoseStatus)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (vaultStatus.isConnected) "Vault Connected" else "Vault Disconnected",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }

                            if (vaultStatus.isConnected) {
                                OutlinedButton(
                                    onClick = { viewModel.disconnectVault() },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.testTag("disconnect_vault_button")
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Disconnect")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (vaultStatus.isConnected) {
                            Text(
                                text = "Vault: ${vaultStatus.vaultName ?: "Obsidian Vault"}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = CyanAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "URI: ${vaultStatus.vaultUri?.take(45)}...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                            )
                        } else {
                            Text(
                                text = "Select your local Obsidian vault folder. PocketAI creates human-readable Markdown notes in structured directories that you can open anytime in Obsidian.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { vaultPickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.testTag("select_vault_button")
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Vault Folder (SAF)", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Sync Toggle
            if (vaultStatus.isConnected) {
                item {
                    Surface(
                        color = SlateDark850,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Inject Obsidian Knowledge into Prompts",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "Allows the assistant to be aware of your connected Markdown vault",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                )
                            }
                            Switch(
                                checked = uiState.isSyncEnabled,
                                onCheckedChange = { viewModel.toggleSync(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = CyanAccent
                                )
                            )
                        }
                    }
                }

                // Verified Folders Checklist
                item {
                    Surface(
                        color = SlateDark900,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Structured Vault Folders",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ObsidianConstants.REQUIRED_FOLDERS.forEach { folderName ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldStatus,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$folderName/",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFCBD5E1),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (folderName) {
                                            "Memory" -> "— User facts and persistent summaries"
                                            "Knowledge" -> "— Synthesized topic notes"
                                            "Conversations" -> "— Exported chat threads"
                                            "Preferences" -> "— System preferences & instructions"
                                            else -> "— Autonomous lessons & insights"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Note Creator
                item {
                    Surface(
                        color = SlateDark850,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Write Markdown Note to Vault",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = quickNoteTitle,
                                onValueChange = { quickNoteTitle = it },
                                label = { Text("Note Title") },
                                placeholder = { Text("e.g. Robot_Kinematics") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
                                colors = dialogTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = quickNoteContent,
                                onValueChange = { quickNoteContent = it },
                                label = { Text("Markdown Content") },
                                placeholder = { Text("# Kinematics\n- Forward kinematics\n- Inverse kinematics") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth().testTag("note_content_input"),
                                colors = dialogTextFieldColors()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = {
                                        viewModel.createKnowledgeNote(quickNoteTitle, quickNoteContent)
                                        quickNoteTitle = ""
                                        quickNoteContent = ""
                                    },
                                    enabled = quickNoteTitle.isNotBlank() && quickNoteContent.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.testTag("save_note_button")
                                ) {
                                    Text("Save Note.md", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
