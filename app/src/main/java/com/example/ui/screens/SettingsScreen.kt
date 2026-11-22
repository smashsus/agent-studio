package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Bell
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.Gauge
import com.adamglin.phosphoricons.regular.HandTap
import com.adamglin.phosphoricons.regular.Palette
import com.adamglin.phosphoricons.regular.ShieldCheck
import com.adamglin.phosphoricons.regular.SlidersHorizontal
import com.adamglin.phosphoricons.regular.Trash
import com.adamglin.phosphoricons.regular.Wrench
import com.example.settings.AutonomyLevel
import com.example.ui.MainViewModel
import com.example.ui.components.SwissDivider
import com.example.ui.components.SwissOutlinedButton
import com.example.ui.components.SwissPrimaryButton
import com.example.ui.components.SwissSectionHeader
import com.example.ui.components.SwissSettingRow
import com.example.ui.components.SwissSurfaceCard
import com.example.ui.components.SwissTopBar
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = LocalSwissTokens.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
            .imePadding()
    ) {
        SwissTopBar(
            title = "Settings",
            subtitle = "Model, autonomy, permissions & theme",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SwissGeometry.screenPadding),
            verticalArrangement = Arrangement.spacedBy(SwissGeometry.sectionSpacing)
        ) {
            // Model & Endpoint Configuration
            item {
                SwissSectionHeader(title = "Model & Endpoint")
                SwissSurfaceCard {
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.SlidersHorizontal,
                        title = "Active Model",
                        description = "Used for compatible API endpoints",
                        currentValue = settings.modelId.ifBlank { "Not configured" }
                    )
                    OutlinedTextField(
                        value = modelIdInput,
                        onValueChange = { modelIdInput = it },
                        label = { Text("Model ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        label = { Text("HTTPS Base URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SwissPrimaryButton(
                        text = "Apply Model Settings",
                        onClick = { viewModel.setModelConfig(modelIdInput, baseUrlInput) },
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            // Autonomy Policy
            item {
                SwissSectionHeader(title = "Autonomy Policy")
                SwissSurfaceCard(padding = PaddingValues(0.dp)) {
                    AutonomyLevel.entries.forEachIndexed { idx, level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAutonomyLevel(level) }
                                .padding(horizontal = SwissGeometry.cardPadding, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.autonomyLevel == level,
                                onClick = { viewModel.setAutonomyLevel(level) },
                                modifier = Modifier.testTag("autonomy_${level.name.lowercase()}")
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(level.label, style = MaterialTheme.typography.titleSmall, color = tokens.primaryText)
                                Text(level.description, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                            }
                        }
                        if (idx < AutonomyLevel.entries.lastIndex) {
                            SwissDivider()
                        }
                    }
                }
            }

            // Execution Limits
            item {
                SwissSectionHeader(title = "Execution Limits")
                SwissSurfaceCard {
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.Gauge,
                        title = "Loop & Retry Bounds",
                        currentValue = "${maxStepsSlider.roundToInt()} steps · ${retrySlider.roundToInt()} retries · ${timeoutSlider.roundToInt()}s timeout"
                    )
                    Text("Max Steps: ${maxStepsSlider.roundToInt()}", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                    Slider(
                        value = maxStepsSlider,
                        onValueChange = { maxStepsSlider = it },
                        valueRange = 2f..25f,
                        onValueChangeFinished = {
                            viewModel.setExecutionLimits(retrySlider.roundToInt(), timeoutSlider.roundToInt(), maxStepsSlider.roundToInt())
                        }
                    )
                    Text("Retry Limit: ${retrySlider.roundToInt()}", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                    Slider(
                        value = retrySlider,
                        onValueChange = { retrySlider = it },
                        valueRange = 0f..5f,
                        onValueChangeFinished = {
                            viewModel.setExecutionLimits(retrySlider.roundToInt(), timeoutSlider.roundToInt(), maxStepsSlider.roundToInt())
                        }
                    )
                    Text("Timeout: ${timeoutSlider.roundToInt()}s", style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
                    Slider(
                        value = timeoutSlider,
                        onValueChange = { timeoutSlider = it },
                        valueRange = 10f..120f,
                        onValueChangeFinished = {
                            viewModel.setExecutionLimits(retrySlider.roundToInt(), timeoutSlider.roundToInt(), maxStepsSlider.roundToInt())
                        }
                    )
                }
            }

            // Network & OS Permissions
            item {
                SwissSectionHeader(title = "Network & Permissions")
                SwissSurfaceCard(padding = PaddingValues(0.dp)) {
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.ShieldCheck,
                        title = "Restrict Network Destinations",
                        description = "Limit research tools to approved HTTPS domains",
                        currentValue = if (settings.restrictNetworkDomains) "Allowlist active" else "All HTTPS permitted",
                        trailing = {
                            Switch(
                                checked = settings.restrictNetworkDomains,
                                onCheckedChange = { viewModel.setNetworkPolicy(it, domainsInput) }
                            )
                        }
                    )
                    if (settings.restrictNetworkDomains) {
                        Column(
                            modifier = Modifier.padding(horizontal = SwissGeometry.cardPadding, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = domainsInput,
                                onValueChange = { domainsInput = it },
                                label = { Text("Allowed Domains (comma-separated)") },
                                shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SwissOutlinedButton(
                                text = "Apply Allowlist",
                                onClick = { viewModel.setNetworkPolicy(true, domainsInput) },
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                    SwissDivider()
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.Bell,
                        title = "Notifications & Reminders",
                        description = "Used for background tasks and scheduled reminders",
                        currentValue = if (notifGranted) "Granted" else "Permission required",
                        trailing = {
                            if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                SwissOutlinedButton(
                                    text = "Grant",
                                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                )
                            }
                        }
                    )
                }
            }

            // Accessibility Automation
            item {
                val a11yConnected = viewModel.isAccessibilityServiceConnected()
                SwissSectionHeader(title = "Accessibility Automation")
                SwissSurfaceCard(padding = PaddingValues(0.dp)) {
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.HandTap,
                        title = "On-Demand Screen Interaction",
                        description = "Explicit consent required; reads non-password text only when requested",
                        currentValue = if (a11yConnected) "System service connected" else "System service inactive",
                        trailing = {
                            Switch(
                                checked = settings.accessibilityUserConsent,
                                onCheckedChange = { viewModel.setAccessibilityConsent(it) },
                                modifier = Modifier.testTag("accessibility_consent_switch")
                            )
                        }
                    )
                    SwissDivider()
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.SlidersHorizontal,
                        title = "Android Accessibility Settings",
                        description = "Enable or disable the system service",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        trailing = {
                            SwissOutlinedButton(
                                text = "Open",
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("open_accessibility_settings_button")
                            )
                        }
                    )
                }
            }

            // Memory & Appearance
            item {
                SwissSectionHeader(title = "Memory & Appearance")
                SwissSurfaceCard(padding = PaddingValues(0.dp)) {
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.Brain,
                        title = "Inject Long-Term Memory",
                        description = "Include approved facts in agent context",
                        trailing = {
                            Switch(
                                checked = settings.longTermMemoryEnabled,
                                onCheckedChange = { viewModel.setMemoryPreferences(it, settings.autoSummarizeTasks) }
                            )
                        }
                    )
                    SwissDivider()
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.Brain,
                        title = "Auto-Save Task Summaries",
                        description = "Store completed mission outcomes in Task Memory",
                        trailing = {
                            Switch(
                                checked = settings.autoSummarizeTasks,
                                onCheckedChange = { viewModel.setMemoryPreferences(settings.longTermMemoryEnabled, it) }
                            )
                        }
                    )
                    SwissDivider()
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.Palette,
                        title = "Theme",
                        currentValue = settings.themeMode.label
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = SwissGeometry.cardPadding, end = SwissGeometry.cardPadding, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = settings.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = tokens.surface2,
                                    selectedContainerColor = tokens.primaryAccent.copy(alpha = 0.14f),
                                    selectedLabelColor = tokens.primaryAccent
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = tokens.border.copy(alpha = 0.45f),
                                    selectedBorderColor = tokens.primaryAccent.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            // Tool Permissions
            item {
                SwissSectionHeader(
                    title = "Tool Capabilities",
                    trailingText = "${viewModel.toolDefinitions.size} registered"
                )
            }

            items(viewModel.toolDefinitions, key = { it.name }) { tool ->
                val enabled = tool.name !in settings.disabledTools
                SwissSurfaceCard(padding = PaddingValues(0.dp)) {
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.Wrench,
                        title = tool.name,
                        description = tool.description,
                        currentValue = "${tool.category} · ${tool.riskLevel.label}",
                        trailing = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { viewModel.setToolEnabled(tool.name, it) }
                            )
                        }
                    )
                }
            }

            // Data Management
            item {
                SwissSectionHeader(title = "Data Management")
                SwissSurfaceCard(padding = PaddingValues(0.dp)) {
                    SwissSettingRow(
                        icon = PhosphorIcons.Regular.Trash,
                        title = "Clear Local History & Memory",
                        description = "Removes threads, task plans, logs, and stored memories",
                        trailing = {
                            SwissOutlinedButton(
                                text = "Clear",
                                onClick = { showClearDataDialog = true },
                                tint = tokens.error,
                                modifier = Modifier.testTag("clear_all_data_button")
                            )
                        }
                    )
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            containerColor = tokens.surface1,
            shape = RoundedCornerShape(SwissGeometry.radiusDialog),
            title = { Text("Clear Local Data?", style = MaterialTheme.typography.titleLarge, color = tokens.primaryText) },
            text = {
                Text(
                    "This deletes all conversation threads, task plans, execution logs, and stored memories from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText
                )
            },
            confirmButton = {
                SwissPrimaryButton(
                    text = "Delete All",
                    onClick = {
                        viewModel.clearAllLocalData()
                        showClearDataDialog = false
                    }
                )
            },
            dismissButton = {
                SwissOutlinedButton(text = "Cancel", onClick = { showClearDataDialog = false })
            }
        )
    }
}
