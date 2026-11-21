package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
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
import com.example.tools.WorkspaceFileInfo
import com.example.ui.MainViewModel
import com.example.ui.components.SwissDivider
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
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Files", tint = tokens.primaryText)
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
                    listOf("all" to "All", "reports" to "Reports", "code" to "Code", "notes" to "Notes", "downloads" to "Downloads").forEach { (key, label) ->
                        FilterChip(
                            selected = categoryFilter == key,
                            onClick = { categoryFilter = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.surface2,
                                selectedLabelColor = tokens.primaryAccent
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
                        icon = Icons.Default.FolderOpen,
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
                        modifier = Modifier.fillMaxWidth().testTag("js_sandbox_input")
                    )
                    SwissPrimaryButton(
                        text = "Run Script",
                        onClick = { viewModel.runJsSnippetInSandbox(jsCode) },
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.testTag("run_js_sandbox_button")
                    )
                    jsOutput?.let { out ->
                        Surface(
                            color = tokens.surface2,
                            shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                            border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
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
                        modifier = Modifier.fillMaxWidth().testTag("shell_sandbox_input")
                    )
                    SwissPrimaryButton(
                        text = "Run Shell Command",
                        onClick = { viewModel.runShellInSandbox(shellCommand) },
                        enabled = shellCommand.isNotBlank(),
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.testTag("run_shell_sandbox_button")
                    )
                    shellOutput?.let { out ->
                        Surface(
                            color = tokens.surface2,
                            shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                            border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
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
        "pdf" -> Icons.Default.PictureAsPdf
        "html", "htm" -> Icons.Default.Html
        "js", "json", "kt", "py" -> Icons.Default.Code
        else -> Icons.Default.Description
    }

    Surface(
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.primaryAccent,
                modifier = Modifier.size(22.dp)
            )
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
                IconButton(onClick = onPreview, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = tokens.secondaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onExportSaf, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Save via SAF", tint = tokens.secondaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = tokens.secondaryText, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = tokens.secondaryText, modifier = Modifier.size(18.dp))
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
