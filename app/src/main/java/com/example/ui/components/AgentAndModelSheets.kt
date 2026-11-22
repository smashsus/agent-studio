package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.Cpu
import com.adamglin.phosphoricons.regular.Key
import com.adamglin.phosphoricons.regular.LockSimple
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.PlugsConnected
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.Trash
import com.adamglin.phosphoricons.regular.Wrench
import com.adamglin.phosphoricons.regular.X
import com.example.agent.AgentProfile
import com.example.agent.AgentProfileStore
import com.example.settings.ProviderRegistryStore
import com.example.settings.SavedProviderProfile
import com.example.tools.ToolDefinition
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentManagerSheet(
    agents: List<AgentProfile>,
    activeAgentId: String,
    allTools: List<ToolDefinition>,
    onDismiss: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onInstallCatalogAgent: (String) -> Unit,
    onSaveAgent: (
        existingId: String?,
        name: String,
        handle: String,
        description: String,
        systemPrompt: String,
        preferredModelId: String,
        allowedTools: List<String>,
        disableAllTools: Boolean
    ) -> Unit,
    onInstallFromSpec: (String) -> Boolean,
    onUninstallAgent: (String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Installed, 1 = Install / Catalog
    var editingAgent by remember { mutableStateOf<AgentProfile?>(null) }
    var showCreateAgentDialog by remember { mutableStateOf(false) }
    var showImportSpecDialog by remember { mutableStateOf(false) }

    val installedAgents = remember(agents) { agents.filter { it.isInstalled } }
    val catalogAgents = remember(agents) { agents.filter { !it.isInstalled } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surface1,
        contentColor = tokens.primaryText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SwissGeometry.screenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Agents",
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.primaryText
                    )
                    Text(
                        text = "${installedAgents.size} installed · Install, switch, or edit instructions & tools",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText
                    )
                }
                SwissPrimaryButton(
                    text = "New Agent",
                    icon = PhosphorIcons.Regular.Plus,
                    onClick = { showCreateAgentDialog = true },
                    modifier = Modifier.testTag("create_custom_agent_button")
                )
            }

            // Tab switcher: Installed | Catalog & Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Installed (${installedAgents.size})",
                    "Catalog (${catalogAgents.size})"
                ).forEachIndexed { idx, label ->
                    val selected = selectedTab == idx
                    FilterChip(
                        selected = selected,
                        onClick = { selectedTab = idx },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = tokens.surface2,
                            selectedContainerColor = tokens.primaryAccent.copy(alpha = 0.14f),
                            selectedLabelColor = tokens.primaryAccent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = tokens.border.copy(alpha = 0.55f),
                            selectedBorderColor = tokens.primaryAccent.copy(alpha = 0.55f)
                        )
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showImportSpecDialog = true },
                    label = { Text("Import MD/JSON", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(containerColor = tokens.surface2),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = tokens.border.copy(alpha = 0.55f)
                    )
                )
            }

            val listToShow = if (selectedTab == 0) installedAgents else catalogAgents
            if (listToShow.isEmpty()) {
                SwissEmptyState(
                    icon = PhosphorIcons.Regular.Cpu,
                    title = if (selectedTab == 0) "No installed agents" else "All catalog agents installed",
                    description = if (selectedTab == 0) "Install one from the Catalog or create a custom agent."
                    else "Create or import a custom Markdown/JSON agent above."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listToShow, key = { it.id }) { agent ->
                        val isActive = agent.id == activeAgentId && agent.isInstalled
                        AgentProfileCard(
                            agent = agent,
                            isActive = isActive,
                            onActivate = {
                                if (agent.isInstalled) onSelectAgent(agent.id)
                                else onInstallCatalogAgent(agent.id)
                                onDismiss()
                            },
                            onEdit = { editingAgent = agent },
                            onUninstall = if (agent.isInstalled && agent.id != AgentProfileStore.DEFAULT_AGENT_ID) {
                                { onUninstallAgent(agent.id) }
                            } else null
                        )
                    }
                }
            }
        }
    }

    if (showCreateAgentDialog || editingAgent != null) {
        AgentEditorDialog(
            initial = editingAgent,
            allTools = allTools,
            onDismiss = {
                showCreateAgentDialog = false
                editingAgent = null
            },
            onSave = { existingId, name, handle, desc, prompt, modelId, tools, disableAll ->
                onSaveAgent(existingId, name, handle, desc, prompt, modelId, tools, disableAll)
                showCreateAgentDialog = false
                editingAgent = null
            }
        )
    }

    if (showImportSpecDialog) {
        AgentSpecImportDialog(
            onDismiss = { showImportSpecDialog = false },
            onInstall = { raw ->
                if (onInstallFromSpec(raw)) {
                    showImportSpecDialog = false
                }
            }
        )
    }
}

@Composable
private fun AgentProfileCard(
    agent: AgentProfile,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onUninstall: (() -> Unit)?
) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = if (isActive) tokens.primaryAccent.copy(alpha = 0.08f) else tokens.surface2.copy(alpha = 0.72f),
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(
            SwissGeometry.borderWidth,
            if (isActive) tokens.primaryAccent.copy(alpha = 0.65f) else tokens.border.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwissGeometry.radiusCard))
            .clickable(onClick = onActivate)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SwissBadge(text = agent.handle, textColor = tokens.primaryAccent)
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isActive) {
                    SwissBadge(text = "Active", textColor = tokens.success)
                } else if (!agent.isInstalled) {
                    SwissBadge(text = "Install", textColor = tokens.primaryAccent)
                }
            }

            Text(
                text = agent.description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val toolLabel = when {
                    agent.disableAllTools -> "No tools"
                    agent.allowedTools.isEmpty() -> "All enabled tools"
                    else -> "${agent.allowedTools.size} tools"
                }
                val modelLabel = agent.preferredModelId.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                Text(
                    text = "$toolLabel$modelLabel",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = tokens.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.PencilSimple,
                            contentDescription = "Edit Agent",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (onUninstall != null) {
                        IconButton(onClick = onUninstall, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.Trash,
                                contentDescription = "Remove Agent",
                                tint = tokens.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentEditorDialog(
    initial: AgentProfile?,
    allTools: List<ToolDefinition>,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        name: String,
        handle: String,
        description: String,
        systemPrompt: String,
        preferredModelId: String,
        allowedTools: List<String>,
        disableAllTools: Boolean
    ) -> Unit
) {
    val tokens = LocalSwissTokens.current
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var handle by rememberSaveable { mutableStateOf(initial?.handle.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var systemPrompt by rememberSaveable { mutableStateOf(initial?.systemPrompt.orEmpty()) }
    var preferredModel by rememberSaveable { mutableStateOf(initial?.preferredModelId.orEmpty()) }
    var disableAllTools by rememberSaveable { mutableStateOf(initial?.disableAllTools ?: false) }
    var useCustomTools by rememberSaveable {
        mutableStateOf(initial?.allowedTools?.isNotEmpty() == true)
    }
    var selectedTools by rememberSaveable {
        mutableStateOf(initial?.allowedTools.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusDialog),
        title = {
            Text(
                text = if (initial == null) "Install Custom Agent" else "Edit ${initial.handle}",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.primaryText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Agent Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Handle (e.g. @reviewer)") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = preferredModel,
                    onValueChange = { preferredModel = it },
                    label = { Text("Override Model ID (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Instructions / Persona") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tool Capabilities",
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.primaryText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("All Tools", !disableAllTools && !useCustomTools, 0),
                        Triple("Custom Set", !disableAllTools && useCustomTools, 1),
                        Triple("Chat Only", disableAllTools, 2)
                    ).forEach { (label, selected, mode) ->
                        FilterChip(
                            selected = selected,
                            onClick = {
                                when (mode) {
                                    0 -> { disableAllTools = false; useCustomTools = false }
                                    1 -> { disableAllTools = false; useCustomTools = true }
                                    2 -> { disableAllTools = true; useCustomTools = false }
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                if (!disableAllTools && useCustomTools) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allTools.forEach { tool ->
                            val picked = tool.name in selectedTools
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    selectedTools = if (picked) selectedTools - tool.name
                                    else selectedTools + tool.name
                                },
                                label = {
                                    Text(
                                        text = tool.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            SwissPrimaryButton(
                text = if (initial == null) "Install Agent" else "Save Changes",
                enabled = name.isNotBlank() || systemPrompt.isNotBlank(),
                onClick = {
                    onSave(
                        initial?.id,
                        name,
                        handle,
                        description,
                        systemPrompt,
                        preferredModel,
                        if (useCustomTools && !disableAllTools) selectedTools else emptyList(),
                        disableAllTools
                    )
                }
            )
        },
        dismissButton = {
            SwissOutlinedButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

@Composable
private fun AgentSpecImportDialog(
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    var rawSpec by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusDialog),
        title = { Text("Import Agent Definition", style = MaterialTheme.typography.titleLarge, color = tokens.primaryText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste OpenCode-style Markdown frontmatter (--- name / handle / model / tools ---) or JSON agent config.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText
                )
                OutlinedTextField(
                    value = rawSpec,
                    onValueChange = { rawSpec = it },
                    placeholder = {
                        Text(
                            "---\nname: Debugger\nhandle: @debug\ntools: [run_shell, read_document]\n---\nFocus on root-cause analysis."
                        )
                    },
                    minLines = 6,
                    maxLines = 10,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            SwissPrimaryButton(
                text = "Install",
                enabled = rawSpec.isNotBlank(),
                onClick = { onInstall(rawSpec) }
            )
        },
        dismissButton = {
            SwissOutlinedButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    providers: List<SavedProviderProfile>,
    activeProviderId: String?,
    activeModelId: String,
    isFetchingModels: Boolean,
    onDismiss: () -> Unit,
    onActivateProvider: (String) -> Unit,
    onFetchModels: (String?) -> Unit,
    onSelectModel: (String?, String) -> Unit,
    onRemoveModel: (String, String) -> Unit,
    onOpenEndpoints: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var customModelInput by rememberSaveable { mutableStateOf("") }

    val activeProvider = remember(providers, activeProviderId) {
        providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()
    }
    val rawModels = remember(activeProvider, activeModelId) {
        val list = activeProvider?.modelIds.orEmpty().toMutableList()
        if (activeModelId.isNotBlank() && activeModelId !in list) {
            list.add(0, activeModelId)
        }
        list.distinct()
    }
    val filteredModels = remember(rawModels, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) rawModels
        else rawModels.filter { it.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surface1,
        contentColor = tokens.primaryText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SwissGeometry.screenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Models & Providers",
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.primaryText
                    )
                    Text(
                        text = activeProvider?.let { "${it.name} · ${rawModels.size} model ID(s)" }
                            ?: "Connect a provider to fetch /models",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = tokens.primaryAccent
                        )
                    } else {
                        IconButton(
                            onClick = { onFetchModels(activeProvider?.id) },
                            modifier = Modifier.size(40.dp).testTag("refresh_models_button")
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.ArrowsClockwise,
                                contentDescription = "Fetch /models",
                                tint = tokens.primaryAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    SwissOutlinedButton(
                        text = "Providers",
                        onClick = {
                            onDismiss()
                            onOpenEndpoints()
                        }
                    )
                }
            }

            if (providers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.forEach { prov ->
                        val selected = prov.id == activeProvider?.id
                        FilterChip(
                            selected = selected,
                            onClick = { onActivateProvider(prov.id) },
                            label = { Text(prov.name, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = tokens.surface2,
                                selectedContainerColor = tokens.primaryAccent.copy(alpha = 0.14f),
                                selectedLabelColor = tokens.primaryAccent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = tokens.border.copy(alpha = 0.55f),
                                selectedBorderColor = tokens.primaryAccent.copy(alpha = 0.55f)
                            )
                        )
                    }
                }
            }

            SwissSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Filter model IDs..."
            )

            // Add custom Model ID inline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    label = { Text("Add or set custom Model ID") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.weight(1f)
                )
                SwissPrimaryButton(
                    text = "Use",
                    enabled = customModelInput.isNotBlank(),
                    onClick = {
                        onSelectModel(activeProvider?.id, customModelInput.trim())
                        customModelInput = ""
                        onDismiss()
                    }
                )
            }

            if (filteredModels.isEmpty()) {
                SwissEmptyState(
                    icon = PhosphorIcons.Regular.PlugsConnected,
                    title = "No model IDs yet",
                    description = "Tap Refresh to pull /models from your endpoint or enter a Model ID above."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredModels, key = { it }) { modelId ->
                        val isSelected = modelId == activeModelId
                        Surface(
                            color = if (isSelected) tokens.primaryAccent.copy(alpha = 0.10f) else tokens.surface2.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(SwissGeometry.radiusStandard),
                            border = BorderStroke(
                                SwissGeometry.borderWidth,
                                if (isSelected) tokens.primaryAccent.copy(alpha = 0.6f) else tokens.border.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(SwissGeometry.radiusStandard))
                                .clickable {
                                    onSelectModel(activeProvider?.id, modelId)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = PhosphorIcons.Regular.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = tokens.primaryAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = modelId,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) tokens.primaryAccent else tokens.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (activeProvider != null && rawModels.size > 1) {
                                    IconButton(
                                        onClick = { onRemoveModel(activeProvider.id, modelId) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = PhosphorIcons.Regular.X,
                                            contentDescription = "Remove model ID",
                                            tint = tokens.mutedText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderEditorDialog(
    initial: SavedProviderProfile?,
    hasExistingKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        name: String,
        baseUrl: String,
        apiKey: String,
        selectedModelId: String,
        customModelIds: List<String>,
        verifyConnection: Boolean
    ) -> Unit
) {
    val tokens = LocalSwissTokens.current
    var nameInput by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrlInput by rememberSaveable { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var modelIdInput by rememberSaveable { mutableStateOf(initial?.selectedModelId.orEmpty()) }
    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var modelList by rememberSaveable { mutableStateOf(initial?.modelIds.orEmpty()) }
    var extraModelInput by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusDialog),
        title = {
            Text(
                text = if (initial == null) "Connect Provider" else "Edit ${initial.name}",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.primaryText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderRegistryStore.builtInPresets.forEach { preset ->
                        val selected = baseUrlInput.trimEnd('/') == preset.baseUrl.trimEnd('/')
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (nameInput.isBlank() || initial == null) nameInput = preset.name
                                baseUrlInput = preset.baseUrl
                                modelIdInput = preset.defaultModelId
                                modelList = (preset.suggestedModels + modelList).distinct()
                            },
                            label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Provider Name (e.g. Fasfas AI)") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("HTTPS Base URL (e.g. https://api.fasfas.ai/v1)") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = {
                        Text(
                            if (hasExistingKey && initial != null) "API Key saved (leave blank to keep)"
                            else "API Key (Bearer token)"
                        )
                    },
                    leadingIcon = {
                        Icon(PhosphorIcons.Regular.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelIdInput,
                    onValueChange = { modelIdInput = it },
                    label = { Text("Default Model ID (e.g. glm-5.3-flash)") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )

                // Add additional Model IDs directly in editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = extraModelInput,
                        onValueChange = { extraModelInput = it },
                        label = { Text("Add Model ID to list") },
                        singleLine = true,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                        modifier = Modifier.weight(1f)
                    )
                    SwissOutlinedButton(
                        text = "Add",
                        enabled = extraModelInput.isNotBlank(),
                        onClick = {
                            val clean = extraModelInput.trim()
                            if (clean.isNotEmpty()) {
                                modelList = (listOf(clean) + modelList).distinct()
                                if (modelIdInput.isBlank()) modelIdInput = clean
                                extraModelInput = ""
                            }
                        }
                    )
                }

                if (modelList.isNotEmpty()) {
                    Text(
                        text = "Saved Model IDs (tap to set active):",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modelList.forEach { id ->
                            val isPrimary = id == modelIdInput.trim()
                            FilterChip(
                                selected = isPrimary,
                                onClick = { modelIdInput = id },
                                label = {
                                    Text(
                                        id,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = PhosphorIcons.Regular.X,
                                        contentDescription = "Remove $id",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { modelList = modelList.filterNot { it == id } }
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave = baseUrlInput.isNotBlank() &&
                (modelIdInput.isNotBlank() || modelList.isNotEmpty()) &&
                (apiKeyInput.isNotBlank() || (initial != null && hasExistingKey))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial != null) {
                    SwissOutlinedButton(
                        text = "Save Edits",
                        enabled = canSave,
                        onClick = {
                            onSave(
                                initial.id,
                                nameInput,
                                baseUrlInput,
                                apiKeyInput,
                                modelIdInput,
                                modelList,
                                false
                            )
                        }
                    )
                }
                SwissPrimaryButton(
                    text = if (initial == null) "Save & Fetch Models" else "Verify & Fetch",
                    enabled = canSave,
                    icon = PhosphorIcons.Regular.LockSimple,
                    onClick = {
                        onSave(
                            initial?.id,
                            nameInput,
                            baseUrlInput,
                            apiKeyInput,
                            modelIdInput,
                            modelList,
                            true
                        )
                    }
                )
            }
        },
        dismissButton = {
            SwissOutlinedButton(text = "Cancel", onClick = onDismiss)
        }
    )
}
