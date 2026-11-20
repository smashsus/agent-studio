package com.example.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tools.WorkspaceFileInfo
import com.example.ui.MainViewModel

@Composable
fun FilesAndSandboxScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val files by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val jsOutput by viewModel.jsSandboxOutput.collectAsStateWithLifecycle()
    val filePreview by viewModel.filePreviewContent.collectAsStateWithLifecycle()

    var jsCode by rememberSaveable {
        mutableStateOf("const primes = [2,3,5,7,11];\nprimes.reduce((a,b) => a + b, 0);")
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sandboxed JavaScript Runner Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Sandboxed JavaScript Evaluator", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Runs inside an isolated WebView sandbox (network, file access, and shell commands blocked).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = jsCode,
                        onValueChange = { jsCode = it },
                        textStyle = MaterialTheme.typography.labelMedium,
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().testTag("js_sandbox_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.runJsSnippetInSandbox(jsCode) },
                            modifier = Modifier.testTag("run_js_sandbox_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run in Sandbox")
                        }
                    }
                    jsOutput?.let { out ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = out,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Workspace Files Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Agent Workspace Files (${files.size})", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.refreshWorkspaceFiles() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Workspace Files")
                }
            }
        }

        if (files.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("No Generated Files Yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Reports (.md / .pdf), code files, and HTML mini-apps generated by DeepSeek Agent appear here organized by category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(files, key = { it.absolutePath }) { file ->
                WorkspaceFileCard(
                    file = file,
                    onPreview = { viewModel.openWorkspaceFilePreview(file) },
                    onExportSaf = {
                        pendingExportPath = file.absolutePath
                        exportLauncher.launch(file.name)
                    },
                    onShare = { viewModel.shareFile(file) },
                    onDelete = { viewModel.deleteWorkspaceFile(file.absolutePath) }
                )
            }
        }
    }

    filePreview?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { viewModel.closeFilePreview() },
            title = { Text(title) },
            text = {
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    item {
                        Text(content, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.copyToClipboard(title, content) }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.closeFilePreview() }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun WorkspaceFileCard(
    file: WorkspaceFileInfo,
    onPreview: () -> Unit,
    onExportSaf: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(file.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${file.sizeBytes} bytes • .${file.extension}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onPreview) {
                        Icon(Icons.Default.Visibility, contentDescription = "Inspect or Preview File")
                    }
                    IconButton(onClick = onExportSaf) {
                        Icon(Icons.Default.Download, contentDescription = "Save to Device via SAF")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share File")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete File")
                    }
                }
            }
        }
    }
}
