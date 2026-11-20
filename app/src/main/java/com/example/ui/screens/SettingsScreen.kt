package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.settings.AutonomyLevel
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalAgentColors
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val agentColors = LocalAgentColors.current

    var modelIdInput by remember(settings.modelId) { mutableStateOf(settings.modelId) }
    var baseUrlInput by remember(settings.customBaseUrl) { mutableStateOf(settings.customBaseUrl) }
    var domainsInput by remember(settings.allowedDomainsCsv) { mutableStateOf(settings.allowedDomainsCsv) }

    var maxStepsSlider by remember(settings.maxSteps) { mutableFloatStateOf(settings.maxSteps.toFloat()) }
    var retrySlider by remember(settings.retryLimit) { mutableFloatStateOf(settings.retryLimit.toFloat()) }
    var timeoutSlider by remember(settings.timeoutSeconds) { mutableFloatStateOf(settings.timeoutSeconds.toFloat()) }

    var showClearDataDialog by rememberSaveable { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(viewModel.hasNotificationPermission()) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Model Configuration
        item {
            SettingsSectionCard(title = "Model Configuration", icon = Icons.Default.Tune) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("deepseek-chat", "deepseek-reasoner").forEach { preset ->
                        FilterChip(
                            selected = modelIdInput == preset,
                            onClick = {
                                modelIdInput = preset
                                viewModel.setModelConfig(preset, baseUrlInput)
                            },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = modelIdInput,
                    onValueChange = { modelIdInput = it },
                    label = { Text("Active Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("API Base URL (Priority 2 / Compatible)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.setModelConfig(modelIdInput, baseUrlInput) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Model Config")
                }
            }
        }

        // Agent Autonomy Level
        item {
            SettingsSectionCard(title = "Agent Autonomy Level", icon = Icons.Default.Security) {
                AutonomyLevel.entries.forEach { level ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.autonomyLevel == level,
                            onClick = { viewModel.setAutonomyLevel(level) },
                            modifier = Modifier.testTag("autonomy_${level.name.lowercase()}")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(level.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                level.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Execution Limits
        item {
            SettingsSectionCard(title = "Execution Limits", icon = Icons.Default.Tune) {
                Text("Max Loop Steps: ${maxStepsSlider.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = maxStepsSlider,
                    onValueChange = { maxStepsSlider = it },
                    valueRange = 2f..25f,
                    onValueChangeFinished = {
                        viewModel.setExecutionLimits(
                            retrySlider.roundToInt(),
                            timeoutSlider.roundToInt(),
                            maxStepsSlider.roundToInt()
                        )
                    }
                )
                Text("Retry Limit: ${retrySlider.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = retrySlider,
                    onValueChange = { retrySlider = it },
                    valueRange = 0f..5f,
                    onValueChangeFinished = {
                        viewModel.setExecutionLimits(
                            retrySlider.roundToInt(),
                            timeoutSlider.roundToInt(),
                            maxStepsSlider.roundToInt()
                        )
                    }
                )
                Text("Tool & Request Timeout: ${timeoutSlider.roundToInt()}s", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = timeoutSlider,
                    onValueChange = { timeoutSlider = it },
                    valueRange = 10f..120f,
                    onValueChangeFinished = {
                        viewModel.setExecutionLimits(
                            retrySlider.roundToInt(),
                            timeoutSlider.roundToInt(),
                            maxStepsSlider.roundToInt()
                        )
                    }
                )
            }
        }

        // Network & Runtime OS Permissions
        item {
            SettingsSectionCard(title = "Network & Android Permissions", icon = Icons.Default.Notifications) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Restrict Network Destinations", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Limit research tools to your approved HTTPS domains.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.restrictNetworkDomains,
                        onCheckedChange = { viewModel.setNetworkPolicy(it, domainsInput) }
                    )
                }
                if (settings.restrictNetworkDomains) {
                    OutlinedTextField(
                        value = domainsInput,
                        onValueChange = { domainsInput = it },
                        label = { Text("Allowed Domains (comma-separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { viewModel.setNetworkPolicy(true, domainsInput) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Apply Allowlist")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifications & Task Reminders", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (notifGranted) "Granted" else "Permission required on Android 13+",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (notifGranted) agentColors.success else agentColors.warning
                        )
                    }
                    if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Grant")
                        }
                    }
                }
            }
        }

        // Opt-In Accessibility Integration
        item {
            SettingsSectionCard(title = "Optional Accessibility Automation", icon = Icons.Default.AccessibilityNew) {
                val a11yConnected = viewModel.isAccessibilityServiceConnected()
                Text(
                    text = "Explicit User Consent Required: When enabled both here and in Android Accessibility Settings, DeepSeek Agent can read non-password screen text or trigger assisted clicks only when explicitly requested.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("I consent to on-demand screen interaction", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.accessibilityUserConsent,
                        onCheckedChange = { viewModel.setAccessibilityConsent(it) },
                        modifier = Modifier.testTag("accessibility_consent_switch")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (a11yConnected) "System Service: Connected" else "System Service: Not Activated",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (a11yConnected) agentColors.success else agentColors.warning
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.testTag("open_accessibility_settings_button")
                    ) {
                        Text("Android Settings")
                    }
                }
            }
        }

        // Memory & Theme Preferences
        item {
            SettingsSectionCard(title = "Memory & Theme", icon = Icons.Default.Palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Long-Term Memory Injection", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.longTermMemoryEnabled,
                        onCheckedChange = { viewModel.setMemoryPreferences(it, settings.autoSummarizeTasks) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Save Task Outcome Summaries", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.autoSummarizeTasks,
                        onCheckedChange = { viewModel.setMemoryPreferences(settings.longTermMemoryEnabled, it) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text("Theme Selection", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Individual Tool Permissions
        item {
            Text("Tool Permissions (${viewModel.toolDefinitions.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(viewModel.toolDefinitions, key = { it.name }) { tool ->
            val enabled = tool.name !in settings.disabledTools
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${tool.name} • ${tool.category}", style = MaterialTheme.typography.titleSmall)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tool.riskLevel.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.setToolEnabled(tool.name, it) }
                    )
                }
            }
        }

        // Data Management
        item {
            SettingsSectionCard(title = "Data Management", icon = Icons.Default.DeleteForever) {
                Text(
                    "All conversations, task plans, execution logs, and memories are stored locally in Room SQLite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showClearDataDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = agentColors.danger),
                    modifier = Modifier.fillMaxWidth().testTag("clear_all_data_button")
                ) {
                    Text("Clear Local History & Memory")
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Local Data?") },
            text = { Text("This deletes all conversation threads, task plans, execution logs, and stored memories from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllLocalData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = agentColors.danger)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}
