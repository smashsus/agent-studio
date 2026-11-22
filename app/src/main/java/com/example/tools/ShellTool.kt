package com.example.tools

import android.content.Context
import com.example.permissions.PermissionManager
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream

class RunShellTool(
    private val context: Context,
    private val fileManager: WorkspaceFileManager,
    private val permissionManager: PermissionManager = PermissionManager(context)
) : AgentTool {

    override val definition = ToolDefinition(
        name = "run_shell",
        category = "Shell & System",
        description = "Executes a sandboxed shell command strictly inside the 'Agent Studio' folder. External device directories (/sdcard, /storage, /data, /etc, /system, ../) are blocked. Supports built-in zip, unzip, curl, and iplookup commands alongside toybox utilities.",
        riskLevel = ToolRiskLevel.SYSTEM,
        parameters = listOf(
            ToolParameter("command", "string", "Shell command to run inside Agent Studio."),
            ToolParameter("working_dir", "string", "Optional subfolder inside Agent Studio (e.g. 'code', 'notes', 'reports', 'downloads', 'archives').", required = false),
            ToolParameter("timeout_seconds", "integer", "Timeout in seconds (1..30, default 15).", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val command = args["command"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing command argument.")
        if (command.isEmpty()) {
            return ToolResult(false, "", error = "Shell command cannot be empty.")
        }
        if (command.length > MAX_COMMAND_LENGTH) {
            return ToolResult(false, "", error = "Shell command exceeds $MAX_COMMAND_LENGTH character limit.")
        }

        val blockedReason = checkBlockedCommand(command, fileManager.rootDir.canonicalPath)
        if (blockedReason != null) {
            return ToolResult(false, "", error = blockedReason)
        }

        val rawWorkDir = args["working_dir"]?.jsonPrimitive?.contentOrNull?.trim()
        val workDir = runCatching { resolveWorkingDir(rawWorkDir) }.getOrElse { err ->
            return ToolResult(false, "", error = err.message ?: "Invalid working_dir.")
        }

        // Route lightweight built-in utilities (zip, unzip, curl, iplookup, python -m zipfile) cleanly
        // so they work reliably on every Android device without needing external binaries or big Python packages.
        tryBuiltInUtilityShim(command, workDir, settings)?.let { return it }

        val timeoutSec = (args["timeout_seconds"]?.jsonPrimitive?.intOrNull ?: DEFAULT_TIMEOUT_SEC)
            .coerceIn(1, MAX_TIMEOUT_SEC)

        return withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                val shellBinary = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
                val tmpDir = File(fileManager.rootDir, ".tmp").apply { mkdirs() }
                val pb = ProcessBuilder(shellBinary, "-c", command).apply {
                    directory(workDir)
                    environment().apply {
                        put("HOME", fileManager.rootDir.absolutePath)
                        put("WORKSPACE", fileManager.rootDir.absolutePath)
                        put("AGENT_STUDIO", fileManager.rootDir.absolutePath)
                        put("TMPDIR", tmpDir.absolutePath)
                        put("CACHE_DIR", tmpDir.absolutePath)
                        put("SHELL", shellBinary)
                        put("TERM", "dumb")
                        put("LC_ALL", "C.UTF-8")
                        if (get("PATH").isNullOrBlank()) {
                            put(
                                "PATH",
                                "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin"
                            )
                        }
                    }
                }

                process = pb.start()
                val proc = process

                coroutineScope {
                    val stdoutDeferred = async(Dispatchers.IO) { readBoundedStream(proc.inputStream) }
                    val stderrDeferred = async(Dispatchers.IO) { readBoundedStream(proc.errorStream) }
                    val exitDeferred = async(Dispatchers.IO) { proc.waitFor() }

                    val exitCode = withTimeoutOrNull(timeoutSec * 1000L) {
                        exitDeferred.await()
                    }

                    if (exitCode == null) {
                        proc.destroyForcibly()
                        val partialOut = runCatching { stdoutDeferred.await() }.getOrDefault("")
                        val partialErr = runCatching { stderrDeferred.await() }.getOrDefault("")
                        val timeoutReport = formatShellOutput(
                            exitCode = -1,
                            workDir = workDir,
                            stdout = partialOut,
                            stderr = partialErr
                        )
                        return@coroutineScope ToolResult(
                            success = false,
                            output = timeoutReport,
                            error = "Shell command timed out after ${timeoutSec}s.\n$timeoutReport"
                        )
                    }

                    val stdout = runCatching { stdoutDeferred.await() }.getOrDefault("")
                    val stderr = runCatching { stderrDeferred.await() }.getOrDefault("")
                    val formatted = formatShellOutput(
                        exitCode = exitCode,
                        workDir = workDir,
                        stdout = stdout,
                        stderr = stderr
                    )
                    ToolResult(success = true, output = formatted)
                }
            } catch (e: Exception) {
                process?.destroyForcibly()
                ToolResult(false, "", error = "Shell execution failed: ${e.message ?: "Unknown error"}")
            } finally {
                process?.let {
                    if (it.isAlive) it.destroyForcibly()
                }
            }
        }
    }

    private suspend fun tryBuiltInUtilityShim(
        command: String,
        workDir: File,
        settings: AgentSettings
    ): ToolResult? {
        // Only intercept single built-in utility commands (without chaining pipes/semicolons)
        if (command.contains("&&") || command.contains("||") || command.contains(";") || command.contains("|")) {
            return null
        }
        val tokens = tokenizeShellArgs(command)
        if (tokens.isEmpty()) return null
        val head = tokens.first().lowercase()

        fun resolveRel(arg: String): String {
            val relFromRoot = workDir.relativeToOrSelf(fileManager.rootDir).path
                .takeIf { it.isNotBlank() && it != "." }
            return if (relFromRoot != null && !arg.contains('/')) "$relFromRoot/$arg" else arg
        }

        return when (head) {
            "zip" -> {
                val flags = tokens.drop(1).filter { it.startsWith("-") }
                val positional = tokens.drop(1).filterNot { it.startsWith("-") }
                if (positional.size < 2) {
                    ToolResult(false, "", error = "Usage: zip [-r] <archive.zip> <source1> [source2...]")
                } else {
                    val dest = positional.first()
                    val sources = positional.drop(1).joinToString(",") { resolveRel(it) }
                    ZipArchiveTool(fileManager).execute(
                        buildJsonObject {
                            put("action", "zip")
                            put("source", sources)
                            put("destination", dest)
                        },
                        settings
                    )
                }
            }
            "unzip" -> {
                val listOnly = tokens.any { it == "-l" }
                val positional = tokens.drop(1).filterNot { it.startsWith("-") }
                if (positional.isEmpty()) {
                    ToolResult(false, "", error = "Usage: unzip [-l] <archive.zip> [dest_subfolder]")
                } else {
                    val dIdx = tokens.indexOf("-d")
                    val dest = if (dIdx in 1 until tokens.lastIndex) tokens[dIdx + 1] else positional.getOrNull(1)
                    ZipArchiveTool(fileManager).execute(
                        buildJsonObject {
                            put("action", if (listOnly) "list" else "unzip")
                            put("source", resolveRel(positional.first()))
                            if (!dest.isNullOrBlank()) put("destination", dest)
                        },
                        settings
                    )
                }
            }
            "iplookup", "nslookup", "host" -> {
                val target = tokens.drop(1).lastOrNull { !it.startsWith("-") } ?: "self"
                IpLookupTool(permissionManager).execute(
                    buildJsonObject { put("target", target) },
                    settings
                )
            }
            "curl" -> {
                var method = "GET"
                var body: String? = null
                var outFile: String? = null
                var url: String? = null
                val headers = linkedMapOf<String, String>()
                var i = 1
                while (i < tokens.size) {
                    when (val tok = tokens[i]) {
                        "-X", "--request" -> if (i + 1 < tokens.size) method = tokens[++i].uppercase()
                        "-d", "--data", "--data-raw" -> if (i + 1 < tokens.size) {
                            body = tokens[++i]
                            if (method == "GET") method = "POST"
                        }
                        "-o", "--output" -> if (i + 1 < tokens.size) outFile = tokens[++i]
                        "-H", "--header" -> if (i + 1 < tokens.size) {
                            val h = tokens[++i]
                            val colon = h.indexOf(':')
                            if (colon > 0) {
                                headers[h.substring(0, colon).trim()] = h.substring(colon + 1).trim()
                            }
                        }
                        "-I", "--head" -> method = "HEAD"
                        else -> if (!tok.startsWith("-") && (tok.startsWith("https://") || tok.startsWith("http://"))) {
                            url = tok
                        }
                    }
                    i++
                }
                if (url.isNullOrBlank()) {
                    ToolResult(false, "", error = "Usage: curl [-X METHOD] [-H \"Key: Value\"] [-d BODY] [-o filename] https://...")
                } else {
                    val hdrJson = if (headers.isNotEmpty()) {
                        buildJsonObject { headers.forEach { (k, v) -> put(k, v) } }.toString()
                    } else null
                    HttpCurlTool(permissionManager, fileManager).execute(
                        buildJsonObject {
                            put("url", url)
                            put("method", method)
                            if (hdrJson != null) put("headers_json", hdrJson)
                            if (body != null) put("body", body)
                            if (outFile != null) put("save_filename", outFile)
                        },
                        settings
                    )
                }
            }
            "python", "python3" -> {
                if (tokens.size >= 4 && tokens[1] == "-m" && tokens[2] == "zipfile") {
                    when {
                        tokens.contains("-c") -> {
                            val idx = tokens.indexOf("-c")
                            val archive = tokens.getOrNull(idx + 1)
                            val srcs = tokens.drop(idx + 2).joinToString(",") { resolveRel(it) }
                            if (archive != null && srcs.isNotBlank()) {
                                return ZipArchiveTool(fileManager).execute(
                                    buildJsonObject {
                                        put("action", "zip")
                                        put("source", srcs)
                                        put("destination", archive)
                                    },
                                    settings
                                )
                            }
                        }
                        tokens.contains("-e") -> {
                            val idx = tokens.indexOf("-e")
                            val archive = tokens.getOrNull(idx + 1)
                            val dest = tokens.getOrNull(idx + 2)
                            if (archive != null) {
                                return ZipArchiveTool(fileManager).execute(
                                    buildJsonObject {
                                        put("action", "unzip")
                                        put("source", resolveRel(archive))
                                        if (dest != null) put("destination", dest)
                                    },
                                    settings
                                )
                            }
                        }
                        tokens.contains("-l") -> {
                            val idx = tokens.indexOf("-l")
                            val archive = tokens.getOrNull(idx + 1)
                            if (archive != null) {
                                return ZipArchiveTool(fileManager).execute(
                                    buildJsonObject {
                                        put("action", "list")
                                        put("source", resolveRel(archive))
                                    },
                                    settings
                                )
                            }
                        }
                    }
                }
                null
            }
            else -> null
        }
    }

    private fun tokenizeShellArgs(command: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < command.length) {
            val c = command[i]
            when {
                quote != null -> {
                    if (c == quote) {
                        quote = null
                    } else if (c == '\\' && quote == '"' && i + 1 < command.length) {
                        cur.append(command[++i])
                    } else {
                        cur.append(c)
                    }
                }
                c == '\'' || c == '"' -> quote = c
                c.isWhitespace() -> {
                    if (cur.isNotEmpty()) {
                        out.add(cur.toString())
                        cur.clear()
                    }
                }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun resolveWorkingDir(rawWorkDir: String?): File {
        val root = fileManager.rootDir.apply {
            mkdirs()
            WorkspaceFileManager.STANDARD_SUBFOLDERS.forEach { File(this, it).mkdirs() }
        }
        if (rawWorkDir.isNullOrBlank() || rawWorkDir == ".") {
            return root
        }
        val cleaned = rawWorkDir.trim()
            .removePrefix("${WorkspaceFileManager.WORKSPACE_DIR_NAME}/")
            .removePrefix("${WorkspaceFileManager.LEGACY_DIR_NAME}/")
            .trim('/')
        if (cleaned.isEmpty()) return root
        val candidate = File(root, cleaned)
        val canonicalRoot = root.canonicalPath
        val canonicalTarget = candidate.canonicalPath
        if (canonicalTarget != canonicalRoot && !canonicalTarget.startsWith("$canonicalRoot${File.separator}")) {
            throw SecurityException("Working directory must remain inside ${WorkspaceFileManager.WORKSPACE_DIR_NAME}.")
        }
        if (!candidate.exists()) {
            candidate.mkdirs()
        }
        if (!candidate.isDirectory) {
            throw IllegalArgumentException("Working directory path is not a directory: $rawWorkDir")
        }
        return candidate
    }

    private fun readBoundedStream(stream: InputStream): String {
        val buffer = ByteArray(4096)
        val sb = StringBuilder()
        var total = 0
        var truncated = false
        stream.buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val remaining = MAX_OUTPUT_CHARS - total
                if (remaining > 0) {
                    val chunk = String(buffer, 0, read, Charsets.UTF_8)
                    if (chunk.length <= remaining) {
                        sb.append(chunk)
                        total += chunk.length
                    } else {
                        sb.append(chunk.substring(0, remaining))
                        total += remaining
                        truncated = true
                    }
                } else {
                    truncated = true
                }
            }
        }
        if (truncated) {
            sb.append("\n... [output truncated at $MAX_OUTPUT_CHARS chars]")
        }
        return sb.toString().trimEnd()
    }

    private fun formatShellOutput(
        exitCode: Int,
        workDir: File,
        stdout: String,
        stderr: String
    ): String = buildString {
        val rel = workDir.relativeToOrSelf(fileManager.rootDir).path
        val displayDir = if (rel.isBlank() || rel == ".") {
            WorkspaceFileManager.WORKSPACE_DIR_NAME
        } else {
            "${WorkspaceFileManager.WORKSPACE_DIR_NAME}/$rel"
        }
        appendLine("Exit Code: $exitCode")
        appendLine("Working Dir: $displayDir")
        appendLine("--- STDOUT ---")
        appendLine(stdout.ifEmpty { "(empty)" })
        if (stderr.isNotEmpty()) {
            appendLine("--- STDERR ---")
            appendLine(stderr)
        }
    }.trimEnd()

    companion object {
        private const val MAX_COMMAND_LENGTH = 16_000
        private const val MAX_OUTPUT_CHARS = 16_000
        private const val DEFAULT_TIMEOUT_SEC = 15
        private const val MAX_TIMEOUT_SEC = 30

        private val blockedPatterns = listOf(
            Regex("""shared_prefs""", RegexOption.IGNORE_CASE),
            Regex("""keystore_vault""", RegexOption.IGNORE_CASE),
            Regex("""auth_meta""", RegexOption.IGNORE_CASE),
            Regex("""\b(dalvikvm|app_process|dex2oat)\b""", RegexOption.IGNORE_CASE),
            Regex("""(^|[\s;|&(>])(\.\./|\.\.(\s|$)|/\.\.)"""),
            Regex("""(^|\s)cd\s+/(\s|$|;)""")
        )

        private val forbiddenRootPrefixes = listOf(
            "/sdcard",
            "/storage",
            "/mnt",
            "/data",
            "/etc",
            "/proc",
            "/sys",
            "/root",
            "/vendor",
            "/product",
            "/apex",
            "/system",
            "/bin",
            "/sbin",
            "/usr",
            "/var",
            "/lib",
            "/cache"
        )

        internal fun checkBlockedCommand(command: String, sandboxCanonicalRoot: String = ""): String? {
            for (pattern in blockedPatterns) {
                if (pattern.containsMatchIn(command)) {
                    return "Command blocked by Agent Studio sandbox policy: directory access outside 'Agent Studio' is disabled."
                }
            }
            // Strip URLs before checking absolute filesystem path tokens
            val withoutUrls = command.replace(Regex("""https?://[^\s'"]+""", RegexOption.IGNORE_CASE), "")
            val absTokens = Regex("""(?:^|[\s;|&(='"])(/[^"'`\s;|&)]*)""").findAll(withoutUrls)
            for (match in absTokens) {
                val path = match.groupValues[1]
                if (path == "/dev/null") continue
                if (sandboxCanonicalRoot.isNotBlank() &&
                    (path == sandboxCanonicalRoot || path.startsWith("$sandboxCanonicalRoot/"))
                ) {
                    continue
                }
                if (path == "/" || forbiddenRootPrefixes.any { path == it || path.startsWith("$it/") }) {
                    return "Command blocked by Agent Studio sandbox policy: cannot access external path '$path'. Only the 'Agent Studio' folder is accessible."
                }
            }
            return null
        }
    }
}
