package com.example.tools

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Inflater

data class WorkspaceFileInfo(
    val name: String,
    val category: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String
)

class WorkspaceFileManager(private val context: Context) {
    val rootDir: File by lazy {
        File(context.filesDir, "AgentWorkspace").apply { mkdirs() }
    }

    private val allowedCategories = setOf("reports", "code", "notes", "downloads")

    fun resolveSafeWorkspaceFile(category: String, fileName: String): File {
        val cleanCategory = category.lowercase().takeIf { it in allowedCategories } ?: "notes"
        val cleanName = fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('.')
            .ifEmpty { "agent_output_${System.currentTimeMillis()}.txt" }
        val dir = File(rootDir, cleanCategory).apply { mkdirs() }
        val target = File(dir, cleanName)
        val canonicalRoot = rootDir.canonicalPath
        val canonicalTarget = target.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw SecurityException("Path traversal outside AgentWorkspace is prohibited.")
        }
        return target
    }

    fun resolveExistingWorkspaceFile(pathOrName: String): File {
        val direct = File(pathOrName)
        val candidate = if (direct.isAbsolute) direct else File(rootDir, pathOrName)
        val canonicalRoot = rootDir.canonicalPath
        val canonicalTarget = candidate.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw SecurityException("Unauthorized file path outside AgentWorkspace.")
        }
        return candidate
    }

    fun listAllFiles(): List<WorkspaceFileInfo> {
        val results = mutableListOf<WorkspaceFileInfo>()
        if (!rootDir.exists()) return emptyList()
        rootDir.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { file ->
            val relParent = file.parentFile?.relativeToOrSelf(rootDir)?.path.orEmpty()
            val cat = if (relParent.isBlank() || relParent == ".") "workspace" else relParent.substringBefore(File.separatorChar)
            results.add(
                WorkspaceFileInfo(
                    name = file.name,
                    category = cat,
                    absolutePath = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    extension = file.extension.lowercase()
                )
            )
        }
        return results.sortedByDescending { it.lastModified }
    }

    suspend fun writeTextFile(category: String, fileName: String, content: String): File =
        withContext(Dispatchers.IO) {
            val file = resolveSafeWorkspaceFile(category, fileName)
            file.writeText(content, Charsets.UTF_8)
            file
        }

    suspend fun writePdfFile(
        category: String,
        fileName: String,
        title: String,
        bodyText: String
    ): File = withContext(Dispatchers.IO) {
        val normalizedName = if (fileName.endsWith(".pdf", true)) fileName else "$fileName.pdf"
        val file = resolveSafeWorkspaceFile(category, normalizedName)
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44f
        val maxWidth = pageWidth - margin * 2

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11f
            typeface = Typeface.SANS_SERIF
            isAntiAlias = true
        }

        val wrappedLines = wrapText(bodyText, bodyPaint, maxWidth)
        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var y = margin + 12f
        page.canvas.drawText(title.take(70), margin, y, titlePaint)
        y += 26f

        for (line in wrappedLines) {
            if (y > pageHeight - margin) {
                document.finishPage(page)
                pageNum++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                y = margin
            }
            page.canvas.drawText(line, margin, y, bodyPaint)
            y += 16f
        }
        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                lines.add("")
                return@forEach
            }
            val words = paragraph.split(Regex("\\s+"))
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
        }
        return lines
    }

    suspend fun readUriText(uriString: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        if (mime.contains("pdf", true) || uriString.endsWith(".pdf", true)) {
            val summary = analyzePdfFromUri(uri)
            return@withContext Pair(mime, summary)
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().take(120_000).toByteArray()
        } ?: throw IllegalStateException("Unable to open user-selected document URI.")
        Pair(mime, String(bytes, Charsets.UTF_8))
    }

    suspend fun analyzePdfFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Cannot open PDF file descriptor.")
        pfd.use { descriptor ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            inspectPdf(descriptor, bytes, uri.lastPathSegment ?: "document.pdf")
        }
    }

    suspend fun analyzePdfFile(file: File): String = withContext(Dispatchers.IO) {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { descriptor ->
            val bytes = file.readBytes()
            inspectPdf(descriptor, bytes, file.name)
        }
    }

    private fun inspectPdf(
        descriptor: ParcelFileDescriptor,
        rawBytes: ByteArray,
        label: String
    ): String {
        val pageSummary = runCatching {
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount
                val firstDims = if (count > 0) {
                    renderer.openPage(0).use { p -> "${p.width}x${p.height}pt" }
                } else "unknown"
                "Pages: $count (Page 1 size: $firstDims)"
            }
        }.getOrElse { "Pages: unavailable (${it.message})" }

        val extractedText = extractTextFromPdfBytes(rawBytes)
        return buildString {
            appendLine("PDF Document: $label")
            appendLine(pageSummary)
            appendLine("File Size: ${rawBytes.size} bytes")
            appendLine("--- Extracted Text ---")
            if (extractedText.isBlank()) {
                appendLine("(No plain or Flate-encoded text streams found; PDF may contain scanned images.)")
            } else {
                appendLine(extractedText.take(8000))
            }
        }
    }

    /**
     * Lightweight pure-Kotlin PDF text stream extractor supporting uncompressed & FlateDecode streams
     * without requiring heavy native PDFium/PDFBox APK bloat on budget devices.
     */
    private fun extractTextFromPdfBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val raw = String(bytes, Charsets.ISO_8859_1)
        val collected = StringBuilder()

        // 1. Decompress FlateDecode streams and extract literal text inside (...) Tj / TJ operators
        val streamRegex = Regex("stream\\r?\\n([\\s\\S]*?)\\r?\\nendstream")
        streamRegex.findAll(raw).forEach { match ->
            val streamData = match.groupValues[1].toByteArray(Charsets.ISO_8859_1)
            val decoded = tryInflate(streamData) ?: match.groupValues[1]
            extractTjLiterals(decoded, collected)
            if (collected.length > 9000) return@forEach
        }

        // 2. Fallback direct scan if streams lacked standard newline delimiters
        if (collected.isBlank()) {
            extractTjLiterals(raw, collected)
        }
        return collected.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun tryInflate(input: ByteArray): String? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(input)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            var total = 0
            while (!inflater.finished() && total < 200_000) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) break
                    if (inflater.needsDictionary()) break
                    break
                }
                out.write(buffer, 0, count)
                total += count
            }
            inflater.end()
            out.toString(Charsets.ISO_8859_1.name())
        }.getOrNull()
    }

    private fun extractTjLiterals(content: String, out: StringBuilder) {
        val textOpRegex = Regex("\\(([^()\\\\]{1,200}(?:\\\\.[^()]{0,50})*)\\)\\s*Tj|\\[(.*?)\\]\\s*TJ")
        textOpRegex.findAll(content).forEach { m ->
            val single = m.groupValues[1]
            val array = m.groupValues[2]
            if (single.isNotEmpty()) {
                out.append(unescapePdfString(single)).append(' ')
            } else if (array.isNotEmpty()) {
                Regex("\\(([^()\\\\]*(?:\\\\.[^()]*)*)\\)").findAll(array).forEach { inner ->
                    out.append(unescapePdfString(inner.groupValues[1]))
                }
                out.append(' ')
            }
        }
    }

    private fun unescapePdfString(s: String): String =
        s.replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")

    suspend fun exportWorkspaceFileToUri(sourcePath: String, destUri: Uri) = withContext(Dispatchers.IO) {
        val file = resolveExistingWorkspaceFile(sourcePath)
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Cannot write to selected destination.")
    }

    fun deleteWorkspaceFile(path: String): Boolean {
        val file = resolveExistingWorkspaceFile(path)
        return file.exists() && file.delete()
    }
}

class ReadDocumentTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "read_document",
        category = "File Management",
        description = "Reads a user-selected document URI (content://) or an agent workspace file.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("uri_or_path", "string", "Content URI from attachment or workspace file path.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val target = args["uri_or_path"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing uri_or_path argument.")
        return runCatching {
            if (target.startsWith("content://")) {
                val (mime, text) = fileManager.readUriText(target)
                ToolResult(true, "Read $mime (${text.length} chars):\n${text.take(8000)}")
            } else {
                val file = fileManager.resolveExistingWorkspaceFile(target)
                if (!file.exists()) {
                    ToolResult(false, "", error = "Workspace file not found: ${file.name}")
                } else if (file.extension.equals("pdf", true)) {
                    val summary = fileManager.analyzePdfFile(file)
                    ToolResult(true, summary)
                } else {
                    val content = file.readText(Charsets.UTF_8).take(10_000)
                    ToolResult(true, "File: ${file.name}\n$content")
                }
            }
        }.getOrElse { err ->
            ToolResult(false, "", error = err.message ?: "Failed to read document.")
        }
    }
}

class AnalyzePdfTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "analyze_pdf",
        category = "File Management",
        description = "Inspects page count, dimensions, and extracts text from a PDF URI or workspace PDF.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("uri_or_path", "string", "PDF content:// URI or workspace PDF path.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val target = args["uri_or_path"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing uri_or_path argument.")
        return runCatching {
            val summary = if (target.startsWith("content://")) {
                fileManager.analyzePdfFromUri(Uri.parse(target))
            } else {
                val file = fileManager.resolveExistingWorkspaceFile(target)
                fileManager.analyzePdfFile(file)
            }
            ToolResult(true, summary)
        }.getOrElse { err ->
            ToolResult(false, "", error = err.message ?: "PDF analysis failed.")
        }
    }
}

class CreateFileTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "create_file",
        category = "File Management",
        description = "Creates a TXT, Markdown (.md), PDF (.pdf), JSON, or HTML file inside AgentWorkspace.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("filename", "string", "Target filename with extension (.txt, .md, .pdf, .html)."),
            ToolParameter("content", "string", "Full file content."),
            ToolParameter("category", "string", "Folder: reports, code, notes, or downloads.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val filename = args["filename"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing filename.")
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult(false, "", error = "Missing content.")
        val category = args["category"]?.jsonPrimitive?.contentOrNull?.trim() ?: "notes"

        return runCatching {
            if (filename.endsWith(".pdf", true)) {
                val file = fileManager.writePdfFile(
                    category = category,
                    fileName = filename,
                    title = filename.removeSuffix(".pdf").replace('_', ' '),
                    bodyText = content
                )
                ToolResult(
                    success = true,
                    output = "Created PDF '${file.name}' (${file.length()} bytes) in AgentWorkspace/$category.",
                    artifactPath = file.absolutePath,
                    artifactMimeType = "application/pdf"
                )
            } else {
                val file = fileManager.writeTextFile(category, filename, content)
                val mime = when (file.extension.lowercase()) {
                    "md" -> "text/markdown"
                    "html" -> "text/html"
                    else -> "text/plain"
                }
                ToolResult(
                    success = true,
                    output = "Created file '${file.name}' (${file.length()} bytes) in AgentWorkspace/$category.",
                    artifactPath = file.absolutePath,
                    artifactMimeType = mime,
                    htmlPreviewContent = if (mime == "text/html") content else null
                )
            }
        }.getOrElse { err ->
            ToolResult(false, "", error = err.message ?: "File creation failed.")
        }
    }
}

class ListWorkspaceFilesTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "list_workspace_files",
        category = "File Management",
        description = "Lists generated files organized across reports, code, notes, and downloads.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = emptyList()
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val files = fileManager.listAllFiles()
        if (files.isEmpty()) {
            return ToolResult(true, "AgentWorkspace is currently empty.")
        }
        val listing = files.joinToString("\n") {
            "- [${it.category}] ${it.name} (${it.sizeBytes} bytes) -> ${it.absolutePath}"
        }
        return ToolResult(true, "Workspace Files (${files.size}):\n$listing")
    }
}
