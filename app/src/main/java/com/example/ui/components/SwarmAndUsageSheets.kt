package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ChartBar
import com.adamglin.phosphoricons.regular.Cpu
import com.adamglin.phosphoricons.regular.Minus
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.Trash
import com.adamglin.phosphoricons.regular.UsersThree
import com.example.agent.AgentProfile
import com.example.agent.SwarmConfig
import com.example.agent.SwarmConfigStore
import com.example.agent.SwarmMemberSlot
import com.example.settings.SavedProviderProfile
import com.example.settings.TokenBucket
import com.example.settings.TokenUsageSnapshot
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwarmConfigSheet(
    swarmConfig: SwarmConfig,
    agents: List<AgentProfile>,
    providers: List<SavedProviderProfile>,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetAgentCount: (Int) -> Unit,
    onSetCritiqueRounds: (Int) -> Unit,
    onAddMember: (agentId: String, providerId: String, modelOverride: String, roleNote: String) -> Unit,
    onUpdateMember: (slotId: String, agentId: String, providerId: String, modelOverride: String, roleNote: String) -> Unit,
    onRemoveMember: (slotId: String) -> Unit,
    onResetDefaults: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingSlot by remember { mutableStateOf<SwarmMemberSlot?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = tokens.primaryAccent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.UsersThree,
                                contentDescription = null,
                                tint = tokens.primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Swarm Mode",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.primaryText
                        )
                        Text(
                            text = "Multi-agent plan · Cross-check critique · Equalization",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText
                        )
                    }
                }
                Switch(
                    checked = swarmConfig.enabled,
                    onCheckedChange = onSetEnabled,
                    modifier = Modifier.testTag("swarm_enable_switch")
                )
            }

            Surface(
                color = tokens.surface2.copy(alpha = 0.72f),
                shape = RoundedCornerShape(SwissGeometry.radiusCard),
                border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Desired Swarm Agents",
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.primaryText
                            )
                            Text(
                                text = "${swarmConfig.members.size} agents (2..${SwarmConfigStore.MAX_SWARM_AGENTS})",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.secondaryText
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (swarmConfig.members.size > 2) {
                                        onSetAgentCount(swarmConfig.members.size - 1)
                                    }
                                },
                                enabled = swarmConfig.members.size > 2,
                                modifier = Modifier.size(38.dp).testTag("swarm_count_minus")
                            ) {
                                Icon(PhosphorIcons.Regular.Minus, contentDescription = "Decrease agent count")
                            }
                            Text(
                                text = "${swarmConfig.members.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.primaryAccent
                            )
                            IconButton(
                                onClick = {
                                    if (swarmConfig.members.size < SwarmConfigStore.MAX_SWARM_AGENTS) {
                                        onSetAgentCount(swarmConfig.members.size + 1)
                                    }
                                },
                                enabled = swarmConfig.members.size < SwarmConfigStore.MAX_SWARM_AGENTS,
                                modifier = Modifier.size(38.dp).testTag("swarm_count_plus")
                            ) {
                                Icon(PhosphorIcons.Regular.Plus, contentDescription = "Increase agent count")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Critique Rounds",
                            style = MaterialTheme.typography.labelLarge,
                            color = tokens.primaryText
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3).forEach { round ->
                                val selected = swarmConfig.maxCritiqueRounds == round
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetCritiqueRounds(round) },
                                    label = { Text("$round", style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = tokens.surface1,
                                        selectedContainerColor = tokens.primaryAccent.copy(alpha = 0.14f),
                                        selectedLabelColor = tokens.primaryAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Swarm Roster (${swarmConfig.members.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.primaryText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwissOutlinedButton(text = "Reset", onClick = onResetDefaults)
                    if (swarmConfig.members.size < SwarmConfigStore.MAX_SWARM_AGENTS) {
                        SwissPrimaryButton(
                            text = "Add Agent",
                            icon = PhosphorIcons.Regular.Plus,
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("swarm_add_member_button")
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 310.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(swarmConfig.members, key = { _, m -> m.slotId }) { index, slot ->
                    val agent = agents.firstOrNull { it.id == slot.agentId }
                    val prov = providers.firstOrNull { it.id == slot.providerId }
                    SwarmMemberCard(
                        index = index,
                        slot = slot,
                        agentName = agent?.name ?: "Agent",
                        agentHandle = agent?.handle ?: "@build",
                        providerName = prov?.name ?: "Active Provider",
                        effectiveModel = slot.modelIdOverride.ifBlank {
                            agent?.preferredModelId.orEmpty().ifBlank { prov?.selectedModelId.orEmpty().ifBlank { "Active Model" } }
                        },
                        canDelete = swarmConfig.members.size > 2,
                        onEdit = { editingSlot = slot },
                        onDelete = { onRemoveMember(slot.slotId) }
                    )
                }
            }
        }
    }

    if (showAddDialog || editingSlot != null) {
        SwarmMemberEditorDialog(
            initial = editingSlot,
            agents = agents,
            providers = providers,
            onDismiss = {
                showAddDialog = false
                editingSlot = null
            },
            onSave = { agentId, providerId, modelOverride, roleNote ->
                val cur = editingSlot
                if (cur == null) {
                    onAddMember(agentId, providerId, modelOverride, roleNote)
                } else {
                    onUpdateMember(cur.slotId, agentId, providerId, modelOverride, roleNote)
                }
                showAddDialog = false
                editingSlot = null
            }
        )
    }
}

@Composable
private fun SwarmMemberCard(
    index: Int,
    slot: SwarmMemberSlot,
    agentName: String,
    agentHandle: String,
    providerName: String,
    effectiveModel: String,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = tokens.surface2.copy(alpha = 0.65f),
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwissGeometry.radiusStandard))
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SwissBadge(text = "#${index + 1} $agentHandle", textColor = tokens.primaryAccent)
                    Text(
                        text = agentName,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (slot.roleNote.isNotBlank()) {
                    Text(
                        text = slot.roleNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "$providerName · $effectiveModel",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = tokens.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.PencilSimple,
                        contentDescription = "Edit Swarm Agent",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.Trash,
                            contentDescription = "Remove Swarm Agent",
                            tint = tokens.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwarmMemberEditorDialog(
    initial: SwarmMemberSlot?,
    agents: List<AgentProfile>,
    providers: List<SavedProviderProfile>,
    onDismiss: () -> Unit,
    onSave: (agentId: String, providerId: String, modelOverride: String, roleNote: String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    var selectedAgentId by rememberSaveable {
        mutableStateOf(initial?.agentId ?: agents.firstOrNull()?.id ?: "build")
    }
    var selectedProviderId by rememberSaveable { mutableStateOf(initial?.providerId.orEmpty()) }
    var modelOverride by rememberSaveable { mutableStateOf(initial?.modelIdOverride.orEmpty()) }
    var roleNote by rememberSaveable { mutableStateOf(initial?.roleNote.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusDialog),
        title = {
            Text(
                text = if (initial == null) "Add Swarm Agent" else "Configure Swarm Agent",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.primaryText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Agent Profile", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    agents.forEach { agent ->
                        FilterChip(
                            selected = selectedAgentId == agent.id,
                            onClick = { selectedAgentId = agent.id },
                            label = { Text("${agent.handle} (${agent.name})", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                if (providers.isNotEmpty()) {
                    Text("Provider Binding", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedProviderId.isBlank(),
                            onClick = { selectedProviderId = "" },
                            label = { Text("Active Default", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        providers.forEach { prov ->
                            FilterChip(
                                selected = selectedProviderId == prov.id,
                                onClick = { selectedProviderId = prov.id },
                                label = { Text(prov.name, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = modelOverride,
                    onValueChange = { modelOverride = it },
                    label = { Text("Model ID Override (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = roleNote,
                    onValueChange = { roleNote = it },
                    label = { Text("Swarm Role / Critique Focus") },
                    placeholder = { Text("e.g. Security Auditor, Architect, Fact Checker") },
                    singleLine = true,
                    shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            SwissPrimaryButton(
                text = if (initial == null) "Add" else "Save",
                onClick = { onSave(selectedAgentId, selectedProviderId, modelOverride, roleNote) }
            )
        },
        dismissButton = {
            SwissOutlinedButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

@Composable
fun TokenUsageOverviewCard(
    snapshot: TokenUsageSnapshot,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val overall = snapshot.overall
    Surface(
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwissGeometry.radiusCard))
            .clickable(onClick = onOpenDetails)
            .testTag("token_usage_overview_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.ChartBar,
                        contentDescription = null,
                        tint = tokens.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Token Usage & Estimates",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.primaryText
                    )
                }
                SwissBadge(
                    text = "${formatTokenCount(overall.totalTokens)} tok",
                    textColor = tokens.primaryAccent
                )
            }
            Text(
                text = "In: ${formatTokenCount(overall.totalInputTokens)} (Instr ${formatTokenCount(overall.inputInstructionsTokens)} · Msgs ${formatTokenCount(overall.inputMessagesTokens)})  |  " +
                    "Out: ${formatTokenCount(overall.totalOutputTokens)} (Think ${formatTokenCount(overall.outputThinkingTokens)} · Reply ${formatTokenCount(overall.outputResponseTokens)})",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = tokens.secondaryText
            )
            Text(
                text = "${snapshot.byProvider.size} provider(s) · ${snapshot.byModel.size} model(s) · Tap for full breakdown",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageSheet(
    snapshot: TokenUsageSnapshot,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0 = Overall, 1 = Per Provider, 2 = Per Model

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
                        text = "Token Usage Analytics",
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.primaryText
                    )
                    Text(
                        text = "Exact API usage when reported + deterministic heuristic estimates",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText
                    )
                }
                SwissOutlinedButton(
                    text = "Reset",
                    onClick = onReset,
                    tint = tokens.error,
                    modifier = Modifier.testTag("reset_token_usage_button")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Overall Profile",
                    "Providers (${snapshot.byProvider.size})",
                    "Models (${snapshot.byModel.size})"
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
                        )
                    )
                }
            }

            val buckets: List<TokenBucket> = when (selectedTab) {
                0 -> listOf(snapshot.overall)
                1 -> snapshot.byProvider.values.sortedByDescending { it.totalTokens }
                else -> snapshot.byModel.values.sortedByDescending { it.totalTokens }
            }

            if (buckets.isEmpty()) {
                SwissEmptyState(
                    icon = PhosphorIcons.Regular.Cpu,
                    title = "No usage recorded yet",
                    description = "Send a message or run Swarm Mode to track per-provider and per-model token usage."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(buckets, key = { idx, b -> "${selectedTab}_${idx}_${b.label}" }) { _, bucket ->
                        TokenBucketCard(bucket)
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenBucketCard(bucket: TokenBucket) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = tokens.surface2.copy(alpha = 0.65f),
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText
                    )
                    if (bucket.subtitle.isNotBlank()) {
                        Text(
                            text = "${bucket.subtitle} · ${bucket.requestCount} calls (${bucket.exactReportedCount} API-reported)",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.mutedText
                        )
                    }
                }
                SwissBadge(
                    text = "${formatTokenCount(bucket.totalTokens)} total",
                    textColor = tokens.primaryAccent
                )
            }

            SwissDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "INPUT · ${formatTokenCount(bucket.totalInputTokens)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText
                    )
                    Text(
                        text = "Instructions & Tools: ${formatTokenCount(bucket.inputInstructionsTokens)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = tokens.secondaryText
                    )
                    Text(
                        text = "User/Chat Messages: ${formatTokenCount(bucket.inputMessagesTokens)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = tokens.secondaryText
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "OUTPUT · ${formatTokenCount(bucket.totalOutputTokens)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText
                    )
                    Text(
                        text = "Thinking/Reasoning: ${formatTokenCount(bucket.outputThinkingTokens)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = tokens.secondaryText
                    )
                    Text(
                        text = "Final Reply/Tools: ${formatTokenCount(bucket.outputResponseTokens)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = tokens.secondaryText
                    )
                }
            }
        }
    }
}

private fun formatTokenCount(count: Long): String =
    NumberFormat.getNumberInstance(Locale.US).format(count)
