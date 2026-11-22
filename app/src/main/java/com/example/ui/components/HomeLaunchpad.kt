package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.duotone.Code
import com.adamglin.phosphoricons.duotone.Globe
import com.adamglin.phosphoricons.duotone.MagnifyingGlass
import com.adamglin.phosphoricons.duotone.TerminalWindow
import com.adamglin.phosphoricons.fill.PushPin
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.ChatsCircle
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import com.adamglin.phosphoricons.regular.Cpu
import com.adamglin.phosphoricons.regular.FolderOpen
import com.adamglin.phosphoricons.regular.PlugsConnected
import com.example.authentication.AuthState
import com.example.authentication.ProviderMode
import com.example.database.ConversationSummary
import com.example.settings.AutonomyLevel
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry

private data class MissionTemplate(
    val category: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val prompt: String
)

@Composable
fun HomeWelcomeSection(
    authState: AuthState,
    preferredMode: ProviderMode,
    modelId: String,
    autonomyLevel: AutonomyLevel,
    toolCount: Int,
    recentSummaries: List<ConversationSummary>,
    activeConversationId: Long?,
    onOpenChat: () -> Unit,
    onSelectQuickMission: (String) -> Unit,
    onSelectConversation: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenEndpoints: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenActivity: () -> Unit
) {
    val tokens = LocalSwissTokens.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SwissGeometry.sectionSpacing)
    ) {
        // Flat-Semi Material Hero Workspace Surface
        Surface(
            color = tokens.surface1,
            shape = RoundedCornerShape(SwissGeometry.radiusCard),
            border = BorderStroke(
                SwissGeometry.borderWidth,
                tokens.border.copy(alpha = if (tokens.isDark) 0.55f else 0.75f)
            ),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(SwissGeometry.cardPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = tokens.primaryAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = PhosphorIcons.Regular.Cpu,
                                    contentDescription = null,
                                    tint = tokens.primaryAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Agent Studio",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.primaryAccent
                            )
                            Text(
                                text = if (authState is AuthState.Authenticated) "Runtime Armed" else "Setup Needed",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.mutedText
                            )
                        }
                    }

                    val heroModelBadge = if (preferredMode == ProviderMode.WEB_SESSION) {
                        "Web Session"
                    } else {
                        modelId.ifBlank { "Set Model ID" }
                    }
                    SwissBadge(
                        text = heroModelBadge,
                        textColor = if (preferredMode != ProviderMode.WEB_SESSION && modelId.isBlank()) tokens.warning else tokens.primaryAccent
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Autonomous Workspace",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.primaryText,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = if (authState is AuthState.Authenticated && preferredMode != ProviderMode.WEB_SESSION) {
                            "Provider-neutral agent runtime with shell, JS sandbox, file artifacts, web research, and Android tools."
                        } else {
                            "Connect any OpenAI-compatible HTTPS endpoint or Web Session to run multi-step local tool workflows."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText
                    )
                }

                // Tonal Capability Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwissBadge(
                        text = "$toolCount Tools Ready",
                        textColor = tokens.secondaryText
                    )
                    SwissBadge(
                        text = autonomyLevel.label,
                        textColor = tokens.secondaryAccent
                    )
                    SwissBadge(
                        text = if (preferredMode == ProviderMode.WEB_SESSION) "Browser Mode" else "API Mode",
                        textColor = tokens.success
                    )
                }

                SwissPrimaryButton(
                    text = "Open Chat Space",
                    onClick = onOpenChat,
                    icon = PhosphorIcons.Regular.ChatsCircle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_open_chat_button")
                )

                SwissDivider()

                // Quick Workspace Shortcuts (Flat-Semi Material Tonal Tiles)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickNavTile(
                        icon = PhosphorIcons.Regular.PlugsConnected,
                        label = "Endpoints",
                        onClick = onOpenEndpoints,
                        modifier = Modifier.weight(1f).testTag("home_quick_endpoints")
                    )
                    QuickNavTile(
                        icon = PhosphorIcons.Regular.FolderOpen,
                        label = "Shell & Files",
                        onClick = onOpenFiles,
                        modifier = Modifier.weight(1f).testTag("home_quick_files")
                    )
                    QuickNavTile(
                        icon = PhosphorIcons.Regular.ClockCounterClockwise,
                        label = "Activity",
                        onClick = onOpenActivity,
                        modifier = Modifier.weight(1f).testTag("home_quick_activity")
                    )
                }
            }
        }

        // Recent & Pinned Threads
        val nonEmptyThreads = recentSummaries.filter { it.messageCount > 0 }.take(3)
        if (nonEmptyThreads.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Threads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.primaryText,
                        modifier = Modifier.semantics { heading() }
                    )
                    Surface(
                        color = tokens.primaryAccent.copy(alpha = 0.11f),
                        shape = CircleShape,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenHistory)
                            .testTag("home_all_threads_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "All (${recentSummaries.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.primaryAccent
                            )
                            Icon(
                                imageVector = PhosphorIcons.Regular.CaretRight,
                                contentDescription = null,
                                tint = tokens.primaryAccent,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                nonEmptyThreads.forEach { summary ->
                    RecentThreadRow(
                        summary = summary,
                        isActive = summary.id == activeConversationId,
                        onClick = { onSelectConversation(summary.id) }
                    )
                }
            }
        }

        // Mission Launchpad (2x2 Flat-Semi Material Grid)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Launchpad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.primaryText,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Tap to load prompt",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.mutedText
                )
            }

            val templates = listOf(
                MissionTemplate(
                    category = "Shell",
                    title = "Inspect Sandbox",
                    description = "Check path, toybox tools, and local files.",
                    icon = PhosphorIcons.Duotone.TerminalWindow,
                    prompt = "Run shell commands to inspect pwd, workspace files, and list available toybox utilities, then summarize."
                ),
                MissionTemplate(
                    category = "Research",
                    title = "Technical Brief",
                    description = "Search web sources and save a Markdown report.",
                    icon = PhosphorIcons.Duotone.MagnifyingGlass,
                    prompt = "Research the latest developments in on-device AI agents and save a concise report to reports/."
                ),
                MissionTemplate(
                    category = "Web App",
                    title = "HTML5 Artifact",
                    description = "Build a self-contained app and preview live.",
                    icon = PhosphorIcons.Duotone.Globe,
                    prompt = "Create an interactive Pomodoro & task focus timer web app as a self-contained HTML file and preview it."
                ),
                MissionTemplate(
                    category = "JS Engine",
                    title = "Run Isolated JS",
                    description = "Execute deterministic scripts in WebView.",
                    icon = PhosphorIcons.Duotone.Code,
                    prompt = "Run JavaScript to compute the first 12 Fibonacci numbers and verify their ratios:\n```js\nlet a=0,b=1,r=[]; for(let i=0;i<12;i++){r.push(a);[a,b]=[b,a+b];} JSON.stringify(r);\n```"
                )
            )

            templates.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { template ->
                        LaunchpadTileCard(
                            template = template,
                            onClick = { onSelectQuickMission(template.prompt) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchpadTileCard(
    template: MissionTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(
            SwissGeometry.borderWidth,
            tokens.border.copy(alpha = if (tokens.isDark) 0.55f else 0.75f)
        ),
        tonalElevation = 0.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 122.dp)
            .clip(RoundedCornerShape(SwissGeometry.radiusCard))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = tokens.primaryAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = template.icon,
                            contentDescription = null,
                            tint = tokens.primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = template.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.primaryAccent
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = template.description,
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
private fun QuickNavTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = tokens.surface2,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(SwissGeometry.radiusStandard))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = tokens.primaryAccent.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tokens.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = tokens.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentThreadRow(
    summary: ConversationSummary,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Surface(
        color = if (isActive) tokens.primaryAccent.copy(alpha = 0.08f) else tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(
            SwissGeometry.borderWidth,
            if (isActive) tokens.primaryAccent.copy(alpha = 0.65f) else tokens.border.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SwissGeometry.minTouchTarget)
            .clip(RoundedCornerShape(SwissGeometry.radiusCard))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = if (summary.isPinned || isActive) tokens.primaryAccent.copy(alpha = 0.14f) else tokens.surface2,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (summary.isPinned) PhosphorIcons.Fill.PushPin else PhosphorIcons.Regular.ChatsCircle,
                        contentDescription = null,
                        tint = if (summary.isPinned || isActive) tokens.primaryAccent else tokens.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = summary.lastMessagePreview
                    ?.replace("\n", " ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "Open thread"
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRelativeTime(summary.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.mutedText
                )
                Spacer(Modifier.height(3.dp))
                Surface(
                    color = tokens.surface2,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${summary.messageCount}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
