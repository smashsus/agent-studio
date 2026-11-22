package com.example.tools

import android.content.Context
import android.net.Uri
import com.example.model.OpenAiCompatibleModelProvider
import com.example.permissions.PermissionManager
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipArchiveTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "zip_archive",
        category = "File Management",
        description = "Compresses ('zip'), extracts ('unzip'), or inspects ('list') .zip archives strictly inside the Agent Studio folder.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("action", "string", "One of: zip, unzip, list."),
            ToolParameter("source", "string", "File(s) (comma-separated), subfolder (e.g. 'notes'), or .zip path inside Agent Studio."),
            ToolParameter("destination", "string", "Output .zip filename (for zip) or target subfolder (for unzip, default 'notes').", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase()?.trim()
                ?: return@withContext ToolResult(false, "", error = "Missing action ('zip', 'unzip', or 'list').")
            val source = args["source"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: return@withContext ToolResult(false, "", error = "Missing source argument.")
            val destination = args["destination"]?.jsonPrimitive?.contentOrNull?.trim()

            runCatching {
                when (action) {
                    "zip" -> performZip(source, destination)
                    "unzip" -> performUnzip(source, destination)
                    "list" -> performList(source)
                    else -> ToolResult(false, "", error = "Unsupported zip action '$action'. Use zip, unzip, or list.")
                }
            }.getOrElse { err ->
                ToolResult(false, "", error = err.message ?: "Archive operation failed.")
            }
        }

    private fun performZip(sourceSpec: String, destination: String?): ToolResult {
        val sources = sourceSpec.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { spec ->
                val resolved = fileManager.resolveExistingWorkspaceFile(spec)
                if (!resolved.exists()) {
                    throw IllegalArgumentException("Source not found in Agent Studio: '$spec'")
                }
                resolved
            }
        if (sources.isEmpty()) {
            return ToolResult(false, "", error = "No valid sources provided to zip.")
        }

        val rawOutName = destination?.takeIf { it.isNotBlank() }
            ?: "${sources.first().nameWithoutExtension.ifBlank { "archive" }}_${System.currentTimeMillis() % 10000}.zip"
        val normalizedZipName = if (rawOutName.endsWith(".zip", true)) rawOutName else "$rawOutName.zip"
        val archiveFile = fileManager.resolveSafeWorkspaceFile("archives", normalizedZipName)

        var entriesAdded = 0
        ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zos ->
            val buffer = ByteArray(8192)
            sources.forEach { src ->
                if (src.isDirectory) {
                    val base = src.parentFile ?: fileManager.rootDir
                    src.walkTopDown()
                        .filter { it.isFile && it != archiveFile && !it.name.startsWith(".") }
                        .forEach { child ->
                            val entryName = child.relativeTo(base).path.replace(File.separatorChar, '/')
                            zos.putNextEntry(ZipEntry(entryName))
                            BufferedInputStream(FileInputStream(child)).use { bis ->
                                var read: Int
                                while (bis.read(buffer).also { read = it } != -1) {
                                    zos.write(buffer, 0, read)
                                }
                            }
                            zos.closeEntry()
                            entriesAdded++
                        }
                } else {
                    zos.putNextEntry(ZipEntry(src.name))
                    BufferedInputStream(FileInputStream(src)).use { bis ->
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            zos.write(buffer, 0, read)
                        }
                    }
                    zos.closeEntry()
                    entriesAdded++
                }
            }
        }

        val rel = fileManager.relativePathOf(archiveFile)
        return ToolResult(
            success = true,
            output = "Created archive '$rel' ($entriesAdded file(s), ${archiveFile.length()} bytes) in Agent Studio.",
            artifactPath = archiveFile.absolutePath,
            artifactMimeType = "application/zip"
        )
    }

    private fun performUnzip(sourceZipSpec: String, destination: String?): ToolResult {
        val zipFile = fileManager.resolveExistingWorkspaceFile(sourceZipSpec)
        if (!zipFile.exists() || !zipFile.isFile) {
            return ToolResult(false, "", error = "Archive not found in Agent Studio: '$sourceZipSpec'")
        }
        val destRel = destination?.takeIf { it.isNotBlank() }
            ?: "archives/${zipFile.nameWithoutExtension.ifBlank { "extracted" }}"
        val destDir = fileManager.resolveSafeRelativePath(destRel, defaultCategory = "archives").apply {
            if (exists() && isFile) {
                throw IllegalArgumentException("Unzip destination must be a directory, not an existing file.")
            }
            mkdirs()
        }
        val canonicalDestRoot = destDir.canonicalPath

        val extracted = mutableListOf<String>()
        var totalBytes = 0L
        val buffer = ByteArray(8192)

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name.trimStart('/')
                if (entryName.isNotBlank()) {
                    val outFile = File(destDir, entryName)
                    val canonicalOut = outFile.canonicalPath
                    // Strict Zip-Slip protection inside target Agent Studio directory
                    if (canonicalOut != canonicalDestRoot &&
                        !canonicalOut.startsWith("$canonicalDestRoot${File.separator}")
                    ) {
                        throw SecurityException("Blocked Zip-Slip entry outside Agent Studio: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                totalBytes += read
                                if (totalBytes > MAX_UNZIP_BYTES) {
                                    throw IllegalStateException("Archive exceeds ${MAX_UNZIP_BYTES / (1024 * 1024)}MB safety extraction limit.")
                                }
                                fos.write(buffer, 0, read)
                            }
                        }
                        extracted.add(fileManager.relativePathOf(outFile))
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val destRelDisplay = fileManager.relativePathOf(destDir)
        return ToolResult(
            success = true,
            output = "Extracted ${extracted.size} file(s) ($totalBytes bytes) into '$destRelDisplay':\n" +
                extracted.take(25).joinToString("\n") { "- $it" }
        )
    }

    private fun performList(sourceZipSpec: String): ToolResult {
        val zipFile = fileManager.resolveExistingWorkspaceFile(sourceZipSpec)
        if (!zipFile.exists() || !zipFile.isFile) {
            return ToolResult(false, "", error = "Archive not found in Agent Studio: '$sourceZipSpec'")
        }
        val entries = mutableListOf<String>()
        ZipFile(zipFile).use { zf ->
            val enumEntries = zf.entries()
            while (enumEntries.hasMoreElements() && entries.size < 100) {
                val e = enumEntries.nextElement()
                val typeTag = if (e.isDirectory) "[DIR]" else "${e.size} B"
                entries.add("- ${e.name} ($typeTag)")
            }
        }
        return ToolResult(
            success = true,
            output = "Archive '${fileManager.relativePathOf(zipFile)}' (${entries.size} entries):\n${entries.joinToString("\n")}"
        )
    }

    companion object {
        private const val MAX_UNZIP_BYTES = 32L * 1024L * 1024L
    }
}

class IpLookupTool(
    private val permissionManager: PermissionManager
) : AgentTool {
    override val definition = ToolDefinition(
        name = "ip_lookup",
        category = "Utilities",
        description = "Lightweight DNS/IP & geolocation lookup tool (supports hostnames, IPv4/IPv6 addresses, or 'self' for public IP).",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("target", "string", "Domain name (e.g. openai.com), IP address, or 'self'.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult =
        withContext(Dispatchers.IO) {
            val rawTarget = args["target"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: return@withContext ToolResult(false, "", error = "Missing target hostname, IP, or 'self'.")
            val target = rawTarget.removePrefix("https://").removePrefix("http://").substringBefore('/').trim()
            if (target.isEmpty()) {
                return@withContext ToolResult(false, "", error = "Target cannot be empty.")
            }

            val report = StringBuilder()
            var primaryIp: String? = null

            if (!target.equals("self", true) && !target.equals("myip", true)) {
                val dnsResult = runCatching { InetAddress.getAllByName(target).toList() }
                dnsResult.onSuccess { addrs ->
                    report.appendLine("DNS Records for $target:")
                    addrs.forEach { addr ->
                        val ip = addr.hostAddress ?: ""
                        if (primaryIp == null) primaryIp = ip
                        val rev = runCatching { addr.canonicalHostName }.getOrNull()
                        val revNote = if (!rev.isNullOrBlank() && rev != ip) " (PTR: $rev)" else ""
                        report.appendLine("- $ip$revNote")
                    }
                }.onFailure { err ->
                    report.appendLine("DNS lookup error for $target: ${err.message}")
                }
            }

            val lookupUrl = if (target.equals("self", true) || target.equals("myip", true)) {
                "https://ipwho.is/"
            } else {
                val ipForGeo = primaryIp ?: target
                "https://ipwho.is/$ipForGeo"
            }

            val permitted = permissionManager.isUrlPermitted(lookupUrl, settings)
            if (permitted.isSuccess) {
                val req = Request.Builder()
                    .url(lookupUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", OpenAiCompatibleModelProvider.USER_AGENT)
                    .get()
                    .build()
                runCatching {
                    OpenAiCompatibleModelProvider.executeRequestWithTlsFallback(
                        req,
                        settings.timeoutSeconds.toLong().coerceIn(5L, 20L)
                    ).use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && body.isNotBlank()) {
                            val obj = Json.parseToJsonElement(body).jsonObject
                            val ip = obj["ip"]?.jsonPrimitive?.contentOrNull
                            val country = obj["country"]?.jsonPrimitive?.contentOrNull
                            val city = obj["city"]?.jsonPrimitive?.contentOrNull
                            val region = obj["region"]?.jsonPrimitive?.contentOrNull
                            val conn = obj["connection"] as? JsonObject
                            val isp = conn?.get("isp")?.jsonPrimitive?.contentOrNull
                            val org = conn?.get("org")?.jsonPrimitive?.contentOrNull
                            val asn = conn?.get("asn")?.jsonPrimitive?.contentOrNull
                            val tz = (obj["timezone"] as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                            report.appendLine("IP Info (${ip ?: primaryIp ?: target}):")
                            if (!country.isNullOrBlank()) report.appendLine("- Location: ${listOfNotNull(city, region, country).joinToString(", ")}")
                            if (!isp.isNullOrBlank() || !org.isNullOrBlank()) report.appendLine("- Network: ${listOfNotNull(isp, org, asn?.let { "AS$it" }).joinToString(" · ")}")
                            if (!tz.isNullOrBlank()) report.appendLine("- Timezone: $tz")
                        }
                    }
                }
            }

            val finalReport = report.toString().trim()
            if (finalReport.isEmpty()) {
                ToolResult(false, "", error = "No DNS or IP lookup data returned for '$target'.")
            } else {
                ToolResult(true, finalReport)
            }
        }
}

class HttpCurlTool(
    private val permissionManager: PermissionManager,
    private val fileManager: WorkspaceFileManager
) : AgentTool {
    override val definition = ToolDefinition(
        name = "http_curl",
        category = "Utilities",
        description = "Lightweight HTTPS curl utility supporting GET/POST/PUT/DELETE/HEAD, custom headers, request body, and saving responses inside Agent Studio/downloads.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("url", "string", "Target HTTPS URL."),
            ToolParameter("method", "string", "HTTP method: GET, POST, PUT, DELETE, or HEAD (default GET).", required = false),
            ToolParameter("headers_json", "string", "Optional JSON object of request headers.", required = false),
            ToolParameter("body", "string", "Optional request body string.", required = false),
            ToolParameter("save_filename", "string", "Optional filename to save response inside Agent Studio/downloads.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult =
        withContext(Dispatchers.IO) {
            val url = args["url"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: return@withContext ToolResult(false, "", error = "Missing url argument.")
            val check = permissionManager.isUrlPermitted(url, settings)
            if (check.isFailure) {
                return@withContext ToolResult(false, "", error = check.exceptionOrNull()?.message ?: "URL blocked.")
            }

            val method = (args["method"]?.jsonPrimitive?.contentOrNull?.uppercase()?.trim() ?: "GET")
                .takeIf { it in setOf("GET", "POST", "PUT", "DELETE", "HEAD") }
                ?: return@withContext ToolResult(false, "", error = "Unsupported HTTP method.")
            val bodyStr = args["body"]?.jsonPrimitive?.contentOrNull
            val saveName = args["save_filename"]?.jsonPrimitive?.contentOrNull?.trim()

            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", OpenAiCompatibleModelProvider.USER_AGENT)

            args["headers_json"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { hdrJson ->
                runCatching {
                    val hdrObj = Json.parseToJsonElement(hdrJson).jsonObject
                    hdrObj.forEach { (k, v) ->
                        val valStr = (v as? JsonPrimitive)?.contentOrNull
                        if (valStr != null && !k.equals("Authorization", true)) {
                            builder.header(k, valStr)
                        }
                    }
                }
            }

            when (method) {
                "GET" -> builder.get()
                "HEAD" -> builder.head()
                "DELETE" -> if (bodyStr != null) builder.delete(bodyStr.toRequestBody("application/json".toMediaType())) else builder.delete()
                "POST" -> builder.post((bodyStr ?: "").toRequestBody("application/json".toMediaType()))
                "PUT" -> builder.put((bodyStr ?: "").toRequestBody("application/json".toMediaType()))
            }

            runCatching {
                val timeout = settings.timeoutSeconds.toLong().coerceIn(5L, 60L)
                OpenAiCompatibleModelProvider.executeRequestWithTlsFallback(builder.build(), timeout).use { resp ->
                    val rawBytes = resp.body?.bytes() ?: ByteArray(0)
                    val contentType = resp.header("Content-Type") ?: "application/octet-stream"
                    var artifactPath: String? = null
                    var savedNote = ""
                    if (!saveName.isNullOrBlank()) {
                        val outFile = fileManager.resolveSafeWorkspaceFile("downloads", saveName)
                        outFile.writeBytes(rawBytes)
                        artifactPath = outFile.absolutePath
                        savedNote = "\nSaved to: ${fileManager.relativePathOf(outFile)} (${rawBytes.size} bytes)"
                    }
                    val preview = String(rawBytes.take(10_000).toByteArray(), Charsets.UTF_8)
                    val out = buildString {
                        appendLine("HTTP ${resp.code} ${resp.message} · $contentType · ${rawBytes.size} bytes$savedNote")
                        if (method != "HEAD" && preview.isNotBlank()) {
                            appendLine("--- Body ---")
                            append(preview)
                        }
                    }.trimEnd()
                    ToolResult(
                        success = resp.isSuccessful,
                        output = out,
                        error = if (resp.isSuccessful) null else out,
                        artifactPath = artifactPath,
                        artifactMimeType = if (artifactPath != null) contentType.substringBefore(';') else null
                    )
                }
            }.getOrElse { err ->
                ToolResult(false, "", error = "curl failed: ${err.message ?: "Network error"}")
            }
        }
}

class OcrExtractTool(
    private val context: Context,
    private val fileManager: WorkspaceFileManager,
    private val ocrHelper: OcrHelper
) : AgentTool {
    override val definition = ToolDefinition(
        name = "ocr_extract",
        category = "File Management",
        description = "Extracts printed text (OCR) from an image URI (content://), an image inside Agent Studio, or a scanned PDF.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("uri_or_path", "string", "content:// image/PDF URI or filename/path inside Agent Studio."),
            ToolParameter("page_start", "integer", "1-based start page if target is a PDF (default 1).", required = false),
            ToolParameter("page_end", "integer", "1-based end page if target is a PDF (optional).", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val target = args["uri_or_path"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing uri_or_path argument.")
        val pageStart = (args["page_start"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
        val pageEnd = args["page_end"]?.jsonPrimitive?.intOrNull?.coerceAtLeast(pageStart)

        return runCatching {
            if (target.startsWith("content://")) {
                val uri = Uri.parse(target)
                val mime = context.contentResolver.getType(uri).orEmpty()
                if (mime.contains("pdf", true) || target.endsWith(".pdf", true)) {
                    val summary = fileManager.analyzePdfFromUri(uri, pageStart, pageEnd, runOcrFallback = true)
                    ToolResult(true, summary)
                } else {
                    val res = ocrHelper.recognizeImageUri(uri)
                    res.fold(
                        onSuccess = { text ->
                            ToolResult(true, if (text.isBlank()) "(OCR detected no readable text in image.)" else "OCR Text:\n$text")
                        },
                        onFailure = { err -> ToolResult(false, "", error = "OCR failed: ${err.message}") }
                    )
                }
            } else {
                val file = fileManager.resolveExistingWorkspaceFile(target)
                if (!file.exists()) {
                    return@runCatching ToolResult(false, "", error = "File not found in Agent Studio: '$target'")
                }
                if (file.extension.equals("pdf", true)) {
                    val summary = fileManager.analyzePdfFile(file, pageStart, pageEnd, runOcrFallback = true)
                    ToolResult(true, summary)
                } else {
                    val res = ocrHelper.recognizeImageFile(file)
                    res.fold(
                        onSuccess = { text ->
                            ToolResult(
                                true,
                                if (text.isBlank()) "(OCR detected no readable text in ${file.name}.)"
                                else "OCR Text (${fileManager.relativePathOf(file)}):\n$text"
                            )
                        },
                        onFailure = { err -> ToolResult(false, "", error = "OCR failed: ${err.message}") }
                    )
                }
            }
        }.getOrElse { err ->
            ToolResult(false, "", error = err.message ?: "OCR extraction failed.")
        }
    }
}
