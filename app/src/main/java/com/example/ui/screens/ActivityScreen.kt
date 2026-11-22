package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.Stop
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import com.adamglin.phosphoricons.regular.Copy
import com.example.database.ExecutionLogEntity
import com.example.database.SubtaskEntity
import com.example.ui.MainViewModel
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissDivider
import com.example.ui.components.SwissEmptyState
import com.example.ui.components.SwissSearchField
import com.example.ui.components.SwissSectionHeader
import com.example.ui.components.SwissSurfaceCard
import com.example.ui.components.SwissTopBar
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val latestPlan by viewModel.latestPlan.collectAsStateWithLifecycle()
    val subtasks by viewModel.subtasks.collectAsStateWithLifecycle()
    val logs by viewModel.executionLogs.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, searchQuery, statusFilter) {
        logs.filter { log ->
            val matchesFilter = statusFilter == "ALL" || log.status.equals(statusFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                log.toolName.contains(searchQuery, ignoreCase = true) ||
                log.resultSummary.contains(searchQuery, ignoreCase = true) ||
                log.argumentsJson.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        SwissTopBar(
            title = "Activity",
            subtitle = "${executionState.phase.label} · ${logs.size} events",
            actions = {
                if (executionState.isRunning) {
                    IconButton(
                        onClick = { viewModel.stopExecution() },
                        modifier = Modifier.size(SwissGeometry.minTouchTarget)
                    ) {
                        Icon(
                            PhosphorIcons.Fill.Stop,
                            contentDescription = "Stop Execution",
                            tint = tokens.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SwissGeometry.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SwissSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search tool calls, arguments, or output..."
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All", "SUCCESS" to "Success", "RUNNING" to "Running", "FAILED" to "Failed", "DENIED" to "Denied").forEach { (key, label) ->
                        val selected = statusFilter == key
                        FilterChip(
                            selected = selected,
                            onClick = { statusFilter = key },
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
                                borderColor = tokens.border.copy(alpha = 0.45f),
                                selectedBorderColor = tokens.primaryAccent.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            if (latestPlan != null && subtasks.isNotEmpty()) {
                item {
                    SwissSectionHeader(
                        title = "Current Plan",
                        trailingText = latestPlan?.status
                    )
                    SwissSurfaceCard {
                        Text(
                            text = latestPlan?.goal.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.primaryText
                        )
                        SwissDivider()
                        subtasks.forEach { st ->
                            SubtaskActivityRow(st)
                        }
                    }
                }
            }

            item {
                SwissSectionHeader(
                    title = "Execution Log",
                    trailingText = "${filteredLogs.size} entries"
                )
            }

            if (filteredLogs.isEmpty()) {
                item {
                    SwissEmptyState(
                        icon = PhosphorIcons.Regular.ClockCounterClockwise,
                        title = "No execution events",
                        description = "Tool invocations, retries, durations, and status outcomes appear here as tasks run."
                    )
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    CompactExecutionLogRow(
                        log = log,
                        onCopy = {
                            viewModel.copyToClipboard(
                                "Execution Log",
                                "${log.toolName} (${log.status})\nArgs: ${log.argumentsJson}\nResult: ${log.resultSummary}"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtaskActivityRow(subtask: SubtaskEntity) {
    val tokens = LocalSwissTokens.current
    val statusColor = when (subtask.status) {
        "COMPLETED" -> tokens.success
        "RUNNING" -> tokens.primaryAccent
        "FAILED", "CANCELLED" -> tokens.error
        else -> tokens.mutedText
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = PhosphorIcons.Fill.CheckCircle,
            contentDescription = subtask.status,
            tint = statusColor,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${subtask.orderIndex + 1}. ${subtask.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (subtask.status == "PENDING") tokens.secondaryText else tokens.primaryText
            )
            if (!subtask.resultSummary.isNullOrBlank()) {
                Text(
                    text = subtask.resultSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = subtask.status.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
    }
}

@Composable
private fun CompactExecutionLogRow(
    log: ExecutionLogEntity,
    onCopy: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    val statusColor = when (log.status) {
        "SUCCESS" -> tokens.success
        "RUNNING" -> tokens.primaryAccent
        "DENIED" -> tokens.warning
        else -> tokens.error
    }

    Surface(
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.55f)),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SwissGeometry.radiusCard))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = log.toolName,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Step ${log.stepIndex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.mutedText
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${log.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText
                    )
                    SwissBadge(
                        text = log.status.lowercase().replaceFirstChar { it.uppercase() },
                        textColor = statusColor
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.resultSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = if (expanded) 10 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.mutedText
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SwissDivider()
                    Text(
                        text = "Arguments: ${log.argumentsJson}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.primaryText
                    )
                    if (log.retryCount > 0) {
                        Text(
                            text = "Retries: ${log.retryCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.warning
                        )
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp).align(Alignment.End)) {
                        Icon(
                            PhosphorIcons.Regular.Copy,
                            contentDescription = "Copy execution trace",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
