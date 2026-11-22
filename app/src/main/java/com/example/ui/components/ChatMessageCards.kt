package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Browser
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretUp
import com.adamglin.phosphoricons.regular.Copy
import com.adamglin.phosphoricons.regular.Eye
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Play
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.ShieldCheck
import com.adamglin.phosphoricons.regular.SlidersHorizontal
import com.adamglin.phosphoricons.regular.TerminalWindow
import com.example.database.MessageEntity
import com.example.execution.ExecutionUiState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val toolJsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
private val shellExitCodeRegex = Regex("""^Exit Code:\s*(-?\d+)""", RegexOption.MULTILINE)

@Composable
fun UserMessageBubble(
    message: MessageEntity,
    onCopy: () -> Unit,
    onEdit: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            color = tokens.surface2,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 6.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.55f)),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 15.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!message.attachmentName.isNullOrBlank()) {
                    SwissBadge(text = "File: ${message.attachmentName}", textColor = tokens.primaryAccent)
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.primaryText,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMessageTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.mutedText,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(
                            PhosphorIcons.Regular.Copy,
                            contentDescription = "Copy prompt",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            PhosphorIcons.Regular.PencilSimple,
                            contentDescription = "Edit prompt",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantEditorialMessage(
    message: MessageEntity,
    modelLabel: String,
    onCopy: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShare: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onPreviewArtifact: (String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    val (reasoningText, mainMarkdown) = remember(message.content) {
        splitReasoningTrace(message.content)
    }
    var showReasoning by rememberSaveable(message.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val resolvedModel = message.toolName?.takeIf { it.isNotBlank() }
            ?: modelLabel.takeIf { it.isNotBlank() }
            ?: "Assistant"

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = tokens.surface2,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(tokens.primaryAccent)
                    )
                    Text(
                        text = resolvedModel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText
                    )
                }
            }
            Text(
                text = formatMessageTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.mutedText
            )
        }

        if (!reasoningText.isNullOrBlank()) {
            Surface(
                color = tokens.secondaryAccent.copy(alpha = 0.08f),
                shape = RoundedCornerShape(SwissGeometry.radiusStandard),
                border = BorderStroke(0.75.dp, tokens.secondaryAccent.copy(alpha = 0.28f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SwissGeometry.radiusStandard))
                    .clickable { showReasoning = !showReasoning }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.SlidersHorizontal,
                                contentDescription = null,
                                tint = tokens.secondaryAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Reasoning Trace",
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.secondaryAccent
                            )
                        }
                        Icon(
                            imageVector = if (showReasoning) PhosphorIcons.Regular.CaretUp else PhosphorIcons.Regular.CaretDown,
                            contentDescription = "Toggle reasoning trace",
                            tint = tokens.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AnimatedVisibility(visible = showReasoning) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SwissDivider()
                            Text(
                                text = reasoningText,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText
                            )
                        }
                    }
                }
            }
        }

        if (mainMarkdown.isNotBlank()) {
            MarkdownMessageContent(
                markdown = mainMarkdown,
                onCopyCode = onCopyCode
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
                Icon(
                    PhosphorIcons.Regular.Copy,
                    contentDescription = "Copy response",
                    tint = tokens.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                Icon(
                    PhosphorIcons.Regular.ShareNetwork,
                    contentDescription = "Share response",
                    tint = tokens.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (onRegenerate != null) {
                IconButton(onClick = onRegenerate, modifier = Modifier.size(38.dp)) {
                    Icon(
                        PhosphorIcons.Regular.ArrowsClockwise,
                        contentDescription = "Regenerate",
                        tint = tokens.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (!message.artifactPath.isNullOrBlank()) {
                Spacer(Modifier.width(6.dp))
                SwissOutlinedButton(
                    text = "Preview",
                    onClick = { onPreviewArtifact(message.artifactPath) },
                    icon = PhosphorIcons.Regular.Eye
                )
            }
        }
    }
}

@Composable
fun ToolExecutionRow(
    message: MessageEntity,
    onCopy: () -> Unit,
    onPreviewArtifact: (String) -> Unit
) {
    val tokens = LocalSwissTokens.current
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }

    val toolName = message.toolName ?: "tool"
    val argSummary = remember(message.toolArgumentsJson) {
        extractPrimaryArgumentSummary(message.toolArgumentsJson)
    }
    val exitCode = remember(toolName, message.content) {
        if (toolName == "run_shell") {
            shellExitCodeRegex.find(message.content)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } else null
    }
    val isHardError = message.content.startsWith("Tool error", ignoreCase = true) ||
        message.content.startsWith("Permission denied", ignoreCase = true) ||
        message.content.startsWith("Shell execution failed", ignoreCase = true) ||
        message.content.startsWith("Command blocked", ignoreCase = true) ||
        exitCode == -1

    val isNonZeroShell = toolName == "run_shell" && exitCode != null && exitCode != 0 && !isHardError

    val badgeText = when {
        isHardError -> "Issue"
        isNonZeroShell -> "Exit $exitCode"
        toolName == "run_shell" && exitCode == 0 -> "Exit 0"
        else -> "Done"
    }
    val badgeColor = when {
        isHardError -> tokens.error
        isNonZeroShell -> tokens.warning
        else -> tokens.success
    }
    val iconTint = when {
        isHardError -> tokens.error
        isNonZeroShell -> tokens.warning
        else -> tokens.primaryAccent
    }

    Surface(
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = iconTint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                PhosphorIcons.Regular.TerminalWindow,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = toolName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = tokens.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            SwissBadge(text = badgeText, textColor = badgeColor)
                        }
                        if (!argSummary.isNullOrBlank()) {
                            Text(
                                text = argSummary,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = tokens.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Icon(
                    if (expanded) PhosphorIcons.Regular.CaretUp else PhosphorIcons.Regular.CaretDown,
                    contentDescription = "Toggle tool output",
                    tint = tokens.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissDivider()
                    if (!argSummary.isNullOrBlank()) {
                        Surface(
                            color = tokens.surface2,
                            shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = argSummary,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp
                                ),
                                color = tokens.primaryText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Surface(
                        color = tokens.background,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                        border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = tokens.secondaryText,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissOutlinedButton(
                            text = "Copy Output",
                            onClick = onCopy,
                            icon = PhosphorIcons.Regular.Copy
                        )
                        if (!message.artifactPath.isNullOrBlank()) {
                            SwissOutlinedButton(
                                text = "Preview File",
                                onClick = { onPreviewArtifact(message.artifactPath) },
                                icon = PhosphorIcons.Regular.Eye
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveExecutionProgressCard(executionState: ExecutionUiState) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = tokens.primaryAccent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.primaryAccent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = tokens.primaryAccent
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = executionState.phase.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.primaryText
                    )
                    executionState.activeToolName?.let { tool ->
                        SwissBadge(text = tool, textColor = tokens.primaryAccent)
                    }
                    if (executionState.currentStep > 0) {
                        Text(
                            text = "Step ${executionState.currentStep}/${executionState.maxSteps}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.mutedText
                        )
                    }
                }
                Text(
                    text = executionState.statusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SystemNoticeRow(message: MessageEntity) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = tokens.warning.copy(alpha = 0.07f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(tokens.warning)
            )
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText
            )
        }
    }
}

@Composable
fun ApprovalCheckpointPanel(
    toolName: String,
    argumentsJson: String,
    reason: String,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    SwissSurfaceCard(
        modifier = modifier,
        borderColor = tokens.warning.copy(alpha = 0.65f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.ShieldCheck,
                contentDescription = null,
                tint = tokens.warning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Approval Required · $toolName",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.warning
            )
        }
        Text(reason, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        Text(
            text = argumentsJson.take(260),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
            color = tokens.primaryText
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwissPrimaryButton(
                text = "Approve",
                onClick = onApprove,
                modifier = Modifier.testTag("approve_tool_button")
            )
            SwissOutlinedButton(
                text = "Decline",
                onClick = onDecline,
                modifier = Modifier.testTag("decline_tool_button")
            )
        }
    }
}

@Composable
fun WebBridgePanel(
    preparedPrompt: String,
    explanation: String,
    hasSuggestedLocalTools: Boolean,
    onCopyAndOpenAuth: () -> Unit,
    onRunLocalTools: () -> Unit,
    onSubmitWebReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    var pastedReply by rememberSaveable { mutableStateOf("") }

    SwissSurfaceCard(
        modifier = modifier,
        borderColor = tokens.primaryAccent.copy(alpha = 0.65f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.Browser,
                contentDescription = null,
                tint = tokens.primaryAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Web Session Bridge",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.primaryAccent
            )
        }
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = tokens.secondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SwissOutlinedButton(
                text = "Open Web Session",
                onClick = onCopyAndOpenAuth,
                icon = PhosphorIcons.Regular.Browser,
                modifier = Modifier.weight(1f).testTag("bridge_open_web_button")
            )
            if (hasSuggestedLocalTools) {
                SwissPrimaryButton(
                    text = "Run Local Tools",
                    onClick = onRunLocalTools,
                    icon = PhosphorIcons.Regular.Play,
                    modifier = Modifier.weight(1f).testTag("bridge_run_local_tools_button")
                )
            }
        }
        OutlinedTextField(
            value = pastedReply,
            onValueChange = { pastedReply = it },
            label = { Text("Paste response (text or tool JSON)") },
            maxLines = 3,
            shape = RoundedCornerShape(SwissGeometry.radiusSmall),
            modifier = Modifier.fillMaxWidth().testTag("bridge_reply_input")
        )
        SwissPrimaryButton(
            text = "Continue Loop",
            onClick = {
                if (pastedReply.isNotBlank()) {
                    val reply = pastedReply
                    pastedReply = ""
                    onSubmitWebReply(reply)
                }
            },
            enabled = pastedReply.isNotBlank(),
            modifier = Modifier.align(Alignment.End).testTag("bridge_submit_reply_button")
        )
    }
}

internal fun splitReasoningTrace(content: String): Pair<String?, String> {
    val trimmed = content.trimStart()
    val prefix = "> **Reasoning Trace:**"
    if (!trimmed.startsWith(prefix)) return null to content

    val lines = trimmed.lines()
    val reasoningLines = mutableListOf<String>()
    var idx = 0
    while (idx < lines.size) {
        val line = lines[idx]
        if (line.startsWith(">")) {
            val cleaned = line.removePrefix(">").trimStart()
                .removePrefix("**Reasoning Trace:**").trimStart()
            if (cleaned.isNotEmpty()) reasoningLines.add(cleaned)
            idx++
        } else {
            break
        }
    }
    val remaining = lines.drop(idx).joinToString("\n").trimStart()
    return reasoningLines.joinToString("\n").ifBlank { null } to remaining
}

internal fun extractPrimaryArgumentSummary(argumentsJson: String?): String? {
    if (argumentsJson.isNullOrBlank()) return null
    return runCatching {
        val obj = toolJsonParser.parseToJsonElement(argumentsJson).jsonObject
        val preferredKeys = listOf("command", "query", "url", "filename", "path", "action", "package_name", "code")
        for (key in preferredKeys) {
            val v = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!v.isNullOrEmpty()) {
                val singleLine = v.replace("\n", " ")
                return@runCatching if (key == "command") "$ $singleLine" else "$key: $singleLine"
            }
        }
        obj.entries.firstOrNull()?.let { (k, v) ->
            val str = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
            "$k: ${str.replace("\n", " ")}"
        }
    }.getOrNull()
}

private fun formatMessageTime(ts: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}
