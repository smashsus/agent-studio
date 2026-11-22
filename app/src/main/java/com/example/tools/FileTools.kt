package com.example.tools

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Inflater

data class WorkspaceFileInfo(
    val name: String,
    val category: String,
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String
)

class WorkspaceFileManager(
    private val context: Context,
    private val ocrHelper: OcrHelper = OcrHelper(context)
) {
    private val legacyDir: File by lazy {
        File(context.filesDir, LEGACY_DIR_NAME)
    }

    val rootDir: File by lazy {
        File(context.filesDir, WORKSPACE_DIR_NAME).apply {
            mkdirs()
            migrateLegacyWorkspaceIfNeeded(this)
            STANDARD_SUBFOLDERS.forEach { File(this, it).mkdirs() }
        }
    }

    private fun migrateLegacyWorkspaceIfNeeded(targetRoot: File) {
        val legacy = legacyDir
        if (!legacy.exists() || !legacy.isDirectory) return
        runCatching {
            legacy.walkTopDown().filter { it.isFile }.forEach { src ->
                val rel = src.relativeTo(legacy).path
                val dest = File(targetRoot, rel)
                dest.parentFile?.mkdirs()
                if (!dest.exists()) {
                    src.copyTo(dest, overwrite = false)
                }
            }
            legacy.deleteRecursively()
        }
    }

    fun resolveSafeWorkspaceFile(category: String, fileName: String): File {
        var cleanedInput = fileName
            .trim()
            .trim('"', '\'')
            .replace('\\', '/')
            .removePrefix("./")
            .removePrefix("/")
        listOf("$WORKSPACE_DIR_NAME/", "$LEGACY_DIR_NAME/").forEach { prefix ->
            if (cleanedInput.startsWith(prefix, ignoreCase = true)) {
                cleanedInput = cleanedInput.substring(prefix.length)
            }
        }

        val segments = cleanedInput.split('/').filter { it.isNotBlank() && it != "." && it != ".." }
        val inferredCategory = if (segments.size > 1 && segments.first().lowercase() in ALLOWED_CATEGORIES) {
            segments.first().lowercase()
        } else {
            category.lowercase().takeIf { it in ALLOWED_CATEGORIES } ?: "notes"
        }

        val rawLeafName = segments.lastOrNull() ?: ""
        val cleanName = rawLeafName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('.')
            .ifEmpty { "agent_output_${System.currentTimeMillis()}.txt" }

        val dir = File(rootDir, inferredCategory).apply { mkdirs() }
        val target = File(dir, cleanName)
        assertInsideRoot(target)
        return target
    }

    fun resolveSafeRelativePath(relativePath: String, defaultCategory: String = "notes"): File {
        var cleaned = stripWorkspacePrefixes(relativePath)
            .trim('/')
        if (cleaned.isBlank()) {
            throw IllegalArgumentException("File path cannot be empty.")
        }
        val parts = cleaned.split('/')
            .filter { it.isNotBlank() && it != "." }
            .map { part ->
                if (part == "..") {
                    throw SecurityException("Path traversal outside $WORKSPACE_DIR_NAME is prohibited.")
                }
                part.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
            }
            .filter { it.isNotBlank() }

        if (parts.isEmpty()) {
            throw IllegalArgumentException("Invalid workspace relative path: $relativePath")
        }

        val candidate = if (parts.size == 1 && defaultCategory in ALLOWED_CATEGORIES) {
            File(File(rootDir, defaultCategory), parts.first())
        } else {
            File(rootDir, parts.joinToString(File.separator))
        }
        candidate.parentFile?.mkdirs()
        assertInsideRoot(candidate)
        return candidate
    }

    /**
     * Resolves an existing file (or directory) inside Agent Studio, supporting:
     * - Exact absolute paths inside Agent Studio (and mapped legacy AgentWorkspace paths)
     * - Explicit relative paths (`reports/plan.md`, `Agent Studio/notes/todo.txt`, `./code/app.html`)
     * - Bare filenames (`plan.md`, `todo.txt`) via recursive lookup across subfolders
     * Never permits escaping the `Agent Studio` sandbox directory.
     */
    fun resolveExistingWorkspaceFile(pathOrName: String): File {
        val raw = pathOrName.trim().trim('"', '\'')
        if (raw.isEmpty()) {
            throw IllegalArgumentException("File path or name cannot be empty.")
        }

        val canonicalRoot = rootDir.canonicalPath
        val direct = File(raw)
        if (direct.isAbsolute) {
            val mappedDirect = if (!direct.exists() && raw.contains("/$LEGACY_DIR_NAME")) {
                File(raw.replace("/$LEGACY_DIR_NAME", "/$WORKSPACE_DIR_NAME"))
            } else direct
            val canonicalTarget = mappedDirect.canonicalPath
            if (canonicalTarget == canonicalRoot || canonicalTarget.startsWith("$canonicalRoot${File.separator}")) {
                return mappedDirect
            }
            throw SecurityException("Unauthorized file path outside $WORKSPACE_DIR_NAME.")
        }

        val normalized = stripWorkspacePrefixes(raw).trim('/')
        if (normalized.isEmpty()) return rootDir
        if (normalized.split('/').any { it == ".." }) {
            throw SecurityException("Path traversal outside $WORKSPACE_DIR_NAME is prohibited.")
        }

        // 1. Direct relative match under Agent Studio
        val directRel = File(rootDir, normalized)
        assertInsideRoot(directRel)
        if (directRel.exists()) return directRel

        // 2. Check immediate standard subfolders
        for (sub in STANDARD_SUBFOLDERS) {
            val candidate = File(File(rootDir, sub), normalized)
            assertInsideRoot(candidate)
            if (candidate.exists()) return candidate
        }

        // 3. Recursive suffix or filename match inside Agent Studio
        val targetLeaf = normalized.substringAfterLast('/')
        val allEntries = rootDir.walkTopDown().maxDepth(6)
            .filter { it != rootDir && !it.name.startsWith(".") }
            .toList()

        val suffixMatch = allEntries.firstOrNull { file ->
            val rel = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
            rel.equals(normalized, ignoreCase = true) ||
                rel.endsWith("/$normalized", ignoreCase = true)
        }
        if (suffixMatch != null) return suffixMatch

        val nameMatch = allEntries.firstOrNull { file ->
            file.name.equals(targetLeaf, ignoreCase = true)
        }
        if (nameMatch != null) return nameMatch

        return directRel
    }

    private fun stripWorkspacePrefixes(raw: String): String {
        var cleaned = raw.trim().trim('"', '\'').replace('\\', '/')
        while (cleaned.startsWith("./")) cleaned = cleaned.removePrefix("./")
        cleaned = cleaned.removePrefix("/")
        listOf("$WORKSPACE_DIR_NAME/", "$LEGACY_DIR_NAME/").forEach { prefix ->
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
            }
        }
        if (cleaned.equals(WORKSPACE_DIR_NAME, ignoreCase = true) ||
            cleaned.equals(LEGACY_DIR_NAME, ignoreCase = true)
        ) {
            return ""
        }
        return cleaned
    }

    private fun assertInsideRoot(target: File) {
        val canonicalRoot = rootDir.canonicalPath
        val canonicalTarget = target.canonicalPath
        if (canonicalTarget != canonicalRoot && !canonicalTarget.startsWith("$canonicalRoot${File.separator}")) {
            throw SecurityException("Path traversal outside $WORKSPACE_DIR_NAME is prohibited.")
        }
    }

    fun relativePathOf(file: File): String =
        file.relativeToOrSelf(rootDir).path.replace(File.separatorChar, '/')

    fun listAllFiles(): List<WorkspaceFileInfo> {
        val results = mutableListOf<WorkspaceFileInfo>()
        if (!rootDir.exists()) return emptyList()
        rootDir.walkTopDown().maxDepth(6)
            .onEnter { dir -> dir == rootDir || !dir.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file ->
                val relPath = relativePathOf(file)
                val relParent = file.parentFile?.relativeToOrSelf(rootDir)?.path.orEmpty()
                val cat = if (relParent.isBlank() || relParent == ".") "workspace" else relParent.substringBefore(File.separatorChar)
                results.add(
                    WorkspaceFileInfo(
                        name = file.name,
                        category = cat,
                        relativePath = relPath,
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
            color = Color.rgb(20, 24, 33)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(36, 42, 54)
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
        if (mime.startsWith("image/", ignoreCase = true)) {
            val ocrText = ocrHelper.recognizeImageUri(uri).getOrElse { "(OCR unavailable: ${it.message})" }
            return@withContext Pair(mime, "Image OCR Text:\n$ocrText")
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().take(120_000).toByteArray()
        } ?: throw IllegalStateException("Unable to open user-selected document URI.")
        Pair(mime, String(bytes, Charsets.UTF_8))
    }

    suspend fun recognizeImageFile(file: File): String =
        ocrHelper.recognizeImageFile(file).getOrElse { "Image '${file.name}' (${file.length()} bytes). OCR note: ${it.message}" }

    suspend fun analyzePdfFromUri(
        uri: Uri,
        pageStart: Int = 1,
        pageEnd: Int? = null,
        runOcrFallback: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Cannot open PDF file descriptor.")
        pfd.use { descriptor ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            inspectPdf(descriptor, bytes, uri.lastPathSegment ?: "document.pdf", pageStart, pageEnd, runOcrFallback)
        }
    }

    suspend fun analyzePdfFile(
        file: File,
        pageStart: Int = 1,
        pageEnd: Int? = null,
        runOcrFallback: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            throw IllegalArgumentException("PDF file not found: ${file.name}")
        }
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { descriptor ->
            val bytes = file.readBytes()
            inspectPdf(descriptor, bytes, file.name, pageStart, pageEnd, runOcrFallback)
        }
    }

    private suspend fun inspectPdf(
        descriptor: ParcelFileDescriptor,
        rawBytes: ByteArray,
        label: String,
        pageStart: Int,
        pageEnd: Int?,
        runOcrFallback: Boolean
    ): String {
        val extractedText = extractTextFromPdfBytes(rawBytes)
        val metadata = extractPdfMetadata(rawBytes)

        var pageSummary = "Pages: unavailable"
        val ocrPagesText = StringBuilder()

        runCatching {
            PdfRenderer(descriptor).use { renderer ->
                val totalPages = renderer.pageCount
                val startIdx = (pageStart - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                val maxEnd = (pageEnd ?: (startIdx + 5)).coerceIn(pageStart, totalPages.coerceAtLeast(1))
                val endIdx = (maxEnd - 1).coerceIn(startIdx, (totalPages - 1).coerceAtLeast(0))

                val dimLines = (startIdx..minOf(endIdx, startIdx + 9)).map { idx ->
                    renderer.openPage(idx).use { p -> "P${idx + 1}: ${p.width}x${p.height}pt" }
                }
                pageSummary = "Pages: $totalPages (${dimLines.joinToString(", ")})"

                // Run OCR automatically if text streams returned minimal/blank text (e.g., scanned PDFs)
                if (runOcrFallback && extractedText.length < 40 && totalPages > 0) {
                    val ocrLastIdx = minOf(endIdx, startIdx + 4) // cap at 5 pages per pass for speed
                    for (idx in startIdx..ocrLastIdx) {
                        val pageText = renderPageAndOcr(renderer, idx)
                        if (pageText.isNotBlank()) {
                            ocrPagesText.appendLine("--- Page ${idx + 1} (OCR) ---")
                            ocrPagesText.appendLine(pageText)
                        }
                    }
                }
            }
        }.onFailure { err ->
            pageSummary = "Pages: unavailable (${err.message})"
        }

        return buildString {
            appendLine("PDF Document: $label")
            appendLine(pageSummary)
            appendLine("File Size: ${rawBytes.size} bytes")
            if (metadata.isNotBlank()) {
                appendLine("Metadata: $metadata")
            }
            appendLine("--- Extracted Text ---")
            when {
                extractedText.isNotBlank() -> appendLine(extractedText.take(10_000))
                ocrPagesText.isNotBlank() -> appendLine(ocrPagesText.toString().take(10_000))
                else -> appendLine("(No embedded text streams found and OCR returned empty text.)")
            }
        }.trimEnd()
    }

    private suspend fun renderPageAndOcr(renderer: PdfRenderer, pageIndex: Int): String {
        return runCatching {
            renderer.openPage(pageIndex).use { page ->
                val scale = 2.0f
                val w = (page.width * scale).toInt().coerceIn(320, 2000)
                val h = (page.height * scale).toInt().coerceIn(320, 2800)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ocrHelper.recognizeBitmap(bitmap).getOrDefault("")
                } finally {
                    bitmap.recycle()
                }
            }
        }.getOrDefault("")
    }

    private fun extractPdfMetadata(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val raw = String(bytes.take(minOf(bytes.size, 64_000)).toByteArray(), Charsets.ISO_8859_1)
        val keys = listOf("Title", "Author", "Subject", "Creator")
        val found = keys.mapNotNull { key ->
            val m = Regex("""/$key\s*\(([^()\\]{1,120})\)""").find(raw)
            m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { "$key=$it" }
        }
        return found.joinToString(" · ")
    }

    /**
     * Lightweight pure-Kotlin PDF text stream extractor supporting:
     * - Uncompressed & FlateDecode compressed streams
     * - Literal (...) Tj / TJ operators
     * - Hex <FEFF...> UTF-16BE and ASCII hex glyph strings inside Tj / TJ
     */
    internal fun extractTextFromPdfBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val raw = String(bytes, Charsets.ISO_8859_1)
        val collected = StringBuilder()

        val streamRegex = Regex("stream\\r?\\n?([\\s\\S]*?)\\r?\\n?endstream")
        streamRegex.findAll(raw).forEach { match ->
            val streamBody = match.groupValues[1]
            val streamData = streamBody.toByteArray(Charsets.ISO_8859_1)
            val decoded = tryInflate(streamData) ?: streamBody
            extractTjLiterals(decoded, collected)
            extractHexLiterals(decoded, collected)
            if (collected.length > 12_000) return@forEach
        }

        if (collected.isBlank()) {
            extractTjLiterals(raw, collected)
            extractHexLiterals(raw, collected)
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
            while (!inflater.finished() && total < 250_000) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    break
                }
                out.write(buffer, 0, count)
                total += count
            }
            inflater.end()
            val res = out.toString(Charsets.ISO_8859_1.name())
            res.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun extractTjLiterals(content: String, out: StringBuilder) {
        val textOpRegex = Regex("\\(([^()\\\\]{1,240}(?:\\\\.[^()]{0,60})*)\\)\\s*Tj|\\[(.*?)\\]\\s*TJ")
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

    private fun extractHexLiterals(content: String, out: StringBuilder) {
        val hexOpRegex = Regex("<([0-9A-Fa-f\\s]{4,400})>\\s*Tj")
        hexOpRegex.findAll(content).forEach { m ->
            val decoded = decodePdfHexString(m.groupValues[1])
            if (decoded.isNotBlank()) {
                out.append(decoded).append(' ')
            }
        }
    }

    private fun decodePdfHexString(rawHex: String): String {
        val hex = rawHex.replace(Regex("\\s+"), "")
        if (hex.length < 2) return ""
        val padded = if (hex.length % 2 == 1) "${hex}0" else hex
        val bytes = ByteArray(padded.length / 2) { idx ->
            padded.substring(idx * 2, idx * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
        }
        val text = if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        } else {
            String(bytes, Charsets.ISO_8859_1)
        }
        return text.filter { it.code in 32..126 || it == '\n' }
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

    companion object {
        const val WORKSPACE_DIR_NAME = "Agent Studio"
        const val LEGACY_DIR_NAME = "AgentWorkspace"
        val STANDARD_SUBFOLDERS = listOf("reports", "code", "notes", "downloads", "archives")
        private val ALLOWED_CATEGORIES = STANDARD_SUBFOLDERS.toSet()
    }
}

class ReadDocumentTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "read_document",
        category = "File Management",
        description = "Reads a user-selected document URI (content://) or any file inside the Agent Studio folder (supports bare filename or relative path).",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("uri_or_path", "string", "Content URI from attachment, relative path (e.g. notes/plan.md), or filename.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val target = args["uri_or_path"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing uri_or_path argument.")
        return runCatching {
            if (target.startsWith("content://")) {
                val (mime, text) = fileManager.readUriText(target)
                ToolResult(true, "Read $mime (${text.length} chars):\n${text.take(10_000)}")
            } else {
                val file = fileManager.resolveExistingWorkspaceFile(target)
                if (!file.exists()) {
                    val available = fileManager.listAllFiles().take(12).joinToString(", ") { it.relativePath }
                        .ifEmpty { "none" }
                    ToolResult(
                        false,
                        "",
                        error = "File not found in Agent Studio: '$target'. Available files: $available"
                    )
                } else if (file.extension.equals("pdf", true)) {
                    val summary = fileManager.analyzePdfFile(file)
                    ToolResult(true, summary)
                } else if (file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "bmp")) {
                    val text = fileManager.recognizeImageFile(file)
                    ToolResult(true, "OCR Result for ${fileManager.relativePathOf(file)}:\n$text")
                } else {
                    val rel = fileManager.relativePathOf(file)
                    val content = file.readText(Charsets.UTF_8).take(12_000)
                    ToolResult(true, "File: $rel (${file.length()} bytes)\n$content")
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
        description = "Inspects PDF pages, metadata, and extracts text (including automatic OCR on scanned PDFs) from a PDF URI or Agent Studio file.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("uri_or_path", "string", "PDF content:// URI, relative path, or filename."),
            ToolParameter("page_start", "integer", "1-based start page for inspection/OCR (default 1).", required = false),
            ToolParameter("page_end", "integer", "1-based end page for inspection/OCR (optional).", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val target = args["uri_or_path"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing uri_or_path argument.")
        val startPage = (args["page_start"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
        val endPage = args["page_end"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(startPage)
        return runCatching {
            val summary = if (target.startsWith("content://")) {
                fileManager.analyzePdfFromUri(Uri.parse(target), pageStart = startPage, pageEnd = endPage)
            } else {
                val file = fileManager.resolveExistingWorkspaceFile(target)
                fileManager.analyzePdfFile(file, pageStart = startPage, pageEnd = endPage)
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
        description = "Creates a TXT, Markdown (.md), PDF (.pdf), JSON, CSV, or HTML file inside Agent Studio.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("filename", "string", "Target filename or relative path with extension (.txt, .md, .pdf, .html, .json)."),
            ToolParameter("content", "string", "Full file content."),
            ToolParameter("category", "string", "Subfolder: notes, reports, code, downloads, or archives.", required = false)
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
                    title = filename.substringAfterLast('/').removeSuffix(".pdf").replace('_', ' '),
                    bodyText = content
                )
                val rel = fileManager.relativePathOf(file)
                ToolResult(
                    success = true,
                    output = "Created PDF '$rel' (${file.length()} bytes) in Agent Studio.",
                    artifactPath = file.absolutePath,
                    artifactMimeType = "application/pdf"
                )
            } else {
                val file = fileManager.writeTextFile(category, filename, content)
                val rel = fileManager.relativePathOf(file)
                val mime = when (file.extension.lowercase()) {
                    "md" -> "text/markdown"
                    "html", "htm" -> "text/html"
                    "json" -> "application/json"
                    else -> "text/plain"
                }
                ToolResult(
                    success = true,
                    output = "Created file '$rel' (${file.length()} bytes) in Agent Studio.",
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
        description = "Lists files organized inside Agent Studio (reports, code, notes, downloads, archives).",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = emptyList()
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val files = fileManager.listAllFiles()
        if (files.isEmpty()) {
            return ToolResult(true, "Agent Studio is currently empty.")
        }
        val listing = files.joinToString("\n") {
            "- ${it.relativePath} (${it.sizeBytes} bytes)"
        }
        return ToolResult(true, "Agent Studio Files (${files.size}):\n$listing")
    }
}
