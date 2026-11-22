package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.Eye
import com.adamglin.phosphoricons.regular.FileCode
import com.adamglin.phosphoricons.regular.FileHtml
import com.adamglin.phosphoricons.regular.FilePdf
import com.adamglin.phosphoricons.regular.FileText
import com.adamglin.phosphoricons.regular.FolderOpen
import com.adamglin.phosphoricons.regular.Play
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.Trash
import com.example.tools.WorkspaceFileInfo
import com.example.ui.MainViewModel
import com.example.ui.components.SwissEmptyState
import com.example.ui.components.SwissOutlinedButton
import com.example.ui.components.SwissPrimaryButton
import com.example.ui.components.SwissSearchField
import com.example.ui.components.SwissSectionHeader
import com.example.ui.components.SwissSurfaceCard
import com.example.ui.components.SwissTopBar
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry

@Composable
fun FilesAndSandboxScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val files by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val jsOutput by viewModel.jsSandboxOutput.collectAsStateWithLifecycle()
    val shellOutput by viewModel.shellSandboxOutput.collectAsStateWithLifecycle()
    val filePreview by viewModel.filePreviewContent.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("all") }
    var jsCode by rememberSaveable {
        mutableStateOf("const primes = [2,3,5,7,11];\nprimes.reduce((a,b) => a + b, 0);")
    }
    var shellCommand by rememberSaveable {
        mutableStateOf("pwd && ls -la")
    }
    var pendingExportPath by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val src = pendingExportPath
        pendingExportPath = null
        if (uri != null && src != null) {
            viewModel.exportFileToSaf(src, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWorkspaceFiles()
    }

    val filteredFiles = remember(files, searchQuery, categoryFilter) {
        files.filter { file ->
            val matchesCat = categoryFilter == "all" || file.category.equals(categoryFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                file.name.contains(searchQuery, ignoreCase = true) ||
                file.extension.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        SwissTopBar(
            title = "Files",
            subtitle = "${files.size} workspace artifacts",
            actions = {
                IconButton(
                    onClick = { viewModel.refreshWorkspaceFiles() },
                    modifier = Modifier.size(SwissGeometry.minTouchTarget)
                ) {
                    Icon(
                        PhosphorIcons.Regular.ArrowsClockwise,
                        contentDescription = "Refresh Files",
                        tint = tokens.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
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
                    placeholder = "Search workspace files..."
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "all" to "All",
                        "workspace" to "Workspace",
                        "reports" to "Reports",
                        "code" to "Code",
                        "notes" to "Notes",
                        "downloads" to "Downloads"
                    ).forEach { (key, label) ->
                        val selected = categoryFilter == key
                        FilterChip(
                            selected = selected,
                            onClick = { categoryFilter = key },
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

            item {
                SwissSectionHeader(
                    title = "Workspace Artifacts",
                    trailingText = "${filteredFiles.size} files"
                )
            }

            if (filteredFiles.isEmpty()) {
                item {
                    SwissEmptyState(
                        icon = PhosphorIcons.Regular.FolderOpen,
                        title = "No matching files",
                        description = "Reports (.md, .pdf), notes, and generated HTML applications appear here."
                    )
                }
            } else {
                itemsIndexed(filteredFiles, key = { _, f -> f.absolutePath }) { idx, file ->
                    CompactWorkspaceFileRow(
                        file = file,
                        onPreview = { viewModel.openWorkspaceFilePreview(file) },
                        onExportSaf = {
                            pendingExportPath = file.absolutePath
                            exportLauncher.launch(file.name)
                        },
                        onShare = { viewModel.shareFile(file) },
                        onDelete = { viewModel.deleteWorkspaceFile(file.absolutePath) }
                    )
                    if (idx < filteredFiles.lastIndex) {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SwissSectionHeader(title = "JavaScript Sandbox", trailingText = "Isolated WebView")
                SwissSurfaceCard {
                    Text(
                        text = "Evaluate scripts inside an isolated sandbox with network, file access, and shell commands disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText
                    )
                    OutlinedTextField(
                        value = jsCode,
                        onValueChange = { jsCode = it },
                        textStyle = MaterialTheme.typography.labelMedium,
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                        modifier = Modifier.fillMaxWidth().testTag("js_sandbox_input")
                    )
                    SwissPrimaryButton(
                        text = "Run Script",
                        onClick = { viewModel.runJsSnippetInSandbox(jsCode) },
                        icon = PhosphorIcons.Regular.Play,
                        modifier = Modifier.testTag("run_js_sandbox_button")
                    )
                    jsOutput?.let { out ->
                        Surface(
                            color = tokens.surface2,
                            shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                            border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = out,
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.primaryText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SwissSectionHeader(title = "Shell Console", trailingText = "/system/bin/sh")
                SwissSurfaceCard {
                    Text(
                        text = "Run POSIX/Toybox shell commands inside the app sandbox (working directory: AgentWorkspace).",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText
                    )
                    OutlinedTextField(
                        value = shellCommand,
                        onValueChange = { shellCommand = it },
                        textStyle = MaterialTheme.typography.labelMedium,
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                        modifier = Modifier.fillMaxWidth().testTag("shell_sandbox_input")
                    )
                    SwissPrimaryButton(
                        text = "Run Shell Command",
                        onClick = { viewModel.runShellInSandbox(shellCommand) },
                        enabled = shellCommand.isNotBlank(),
                        icon = PhosphorIcons.Regular.Play,
                        modifier = Modifier.testTag("run_shell_sandbox_button")
                    )
                    shellOutput?.let { out ->
                        Surface(
                            color = tokens.surface2,
                            shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                            border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = out,
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.primaryText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    filePreview?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { viewModel.closeFilePreview() },
            containerColor = tokens.surface1,
            shape = RoundedCornerShape(SwissGeometry.radiusDialog),
            title = { Text(title, style = MaterialTheme.typography.titleLarge, color = tokens.primaryText) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    item {
                        Text(content, style = MaterialTheme.typography.bodySmall, color = tokens.primaryText)
                    }
                }
            },
            confirmButton = {
                SwissPrimaryButton(
                    text = "Copy",
                    onClick = { viewModel.copyToClipboard(title, content) }
                )
            },
            dismissButton = {
                SwissOutlinedButton(
                    text = "Close",
                    onClick = { viewModel.closeFilePreview() }
                )
            }
        )
    }
}

@Composable
private fun CompactWorkspaceFileRow(
    file: WorkspaceFileInfo,
    onPreview: () -> Unit,
    onExportSaf: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    val icon = when (file.extension) {
        "pdf" -> PhosphorIcons.Regular.FilePdf
        "html", "htm" -> PhosphorIcons.Regular.FileHtml
        "js", "json", "kt", "py" -> PhosphorIcons.Regular.FileCode
        else -> PhosphorIcons.Regular.FileText
    }

    Surface(
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusCard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.55f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = tokens.primaryAccent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tokens.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.category} · ${formatFileSize(file.sizeBytes)} · .${file.extension}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreview, modifier = Modifier.size(38.dp)) {
                    Icon(PhosphorIcons.Regular.Eye, contentDescription = "Preview", tint = tokens.secondaryText, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onExportSaf, modifier = Modifier.size(38.dp)) {
                    Icon(PhosphorIcons.Regular.DownloadSimple, contentDescription = "Save via SAF", tint = tokens.secondaryText, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                    Icon(PhosphorIcons.Regular.ShareNetwork, contentDescription = "Share", tint = tokens.secondaryText, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(PhosphorIcons.Regular.Trash, contentDescription = "Delete", tint = tokens.secondaryText, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
