package com.example.tools

import android.content.Context
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

class RunShellTool(
    private val context: Context,
    private val fileManager: WorkspaceFileManager
) : AgentTool {

    override val definition = ToolDefinition(
        name = "run_shell",
        category = "Shell & System",
        description = "Executes a local shell command via /system/bin/sh inside the Android app sandbox (defaults to AgentWorkspace). Captures stdout, stderr, and exit code.",
        riskLevel = ToolRiskLevel.SYSTEM,
        parameters = listOf(
            ToolParameter("command", "string", "Shell command or pipeline to run under /system/bin/sh -c."),
            ToolParameter("working_dir", "string", "Optional subfolder inside AgentWorkspace (e.g. 'code', 'notes', 'reports', 'downloads').", required = false),
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

        val blockedReason = checkBlockedCommand(command)
        if (blockedReason != null) {
            return ToolResult(false, "", error = blockedReason)
        }

        val rawWorkDir = args["working_dir"]?.jsonPrimitive?.contentOrNull?.trim()
        val workDir = runCatching { resolveWorkingDir(rawWorkDir) }.getOrElse { err ->
            return ToolResult(false, "", error = err.message ?: "Invalid working_dir.")
        }

        val timeoutSec = (args["timeout_seconds"]?.jsonPrimitive?.intOrNull ?: DEFAULT_TIMEOUT_SEC)
            .coerceIn(1, MAX_TIMEOUT_SEC)

        return withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                val shellBinary = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
                val pb = ProcessBuilder(shellBinary, "-c", command).apply {
                    directory(workDir)
                    environment().apply {
                        put("HOME", fileManager.rootDir.absolutePath)
                        put("WORKSPACE", fileManager.rootDir.absolutePath)
                        put("TMPDIR", context.cacheDir.absolutePath)
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

                val completed = coroutineScope {
                    val stdoutDeferred = async(Dispatchers.IO) { readBoundedStream(proc.inputStream) }
                    val stderrDeferred = async(Dispatchers.IO) { readBoundedStream(proc.errorStream) }

                    val finished = withTimeoutOrNull(timeoutSec * 1000L) {
                        var done = proc.waitFor(100, TimeUnit.MILLISECONDS)
                        while (!done) {
                            kotlinx.coroutines.delay(50)
                            done = proc.waitFor(50, TimeUnit.MILLISECONDS)
                        }
                        true
                    }

                    if (finished == null) {
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

                    val stdout = stdoutDeferred.await()
                    val stderr = stderrDeferred.await()
                    val exitCode = proc.exitValue()
                    val formatted = formatShellOutput(
                        exitCode = exitCode,
                        workDir = workDir,
                        stdout = stdout,
                        stderr = stderr
                    )

                    if (exitCode == 0) {
                        ToolResult(success = true, output = formatted)
                    } else {
                        ToolResult(
                            success = false,
                            output = formatted,
                            error = "Shell exited with code $exitCode.\n$formatted"
                        )
                    }
                }
                completed
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

    private fun resolveWorkingDir(rawWorkDir: String?): File {
        val root = fileManager.rootDir.apply { mkdirs() }
        if (rawWorkDir.isNullOrBlank() || rawWorkDir == ".") {
            return root
        }
        val candidate = File(root, rawWorkDir)
        val canonicalRoot = root.canonicalPath
        val canonicalTarget = candidate.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw SecurityException("Working directory must remain inside AgentWorkspace.")
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
        appendLine("Exit Code: $exitCode")
        appendLine("Working Dir: ${workDir.absolutePath}")
        if (stdout.isNotEmpty()) {
            appendLine("--- STDOUT ---")
            appendLine(stdout)
        } else {
            appendLine("--- STDOUT ---")
            appendLine("(empty)")
        }
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
            Regex("""deepseek_auth_meta""", RegexOption.IGNORE_CASE),
            Regex("""\b(dalvikvm|app_process|dex2oat)\b""", RegexOption.IGNORE_CASE)
        )

        internal fun checkBlockedCommand(command: String): String? {
            for (pattern in blockedPatterns) {
                if (pattern.containsMatchIn(command)) {
                    return "Command blocked by sandbox security policy (${pattern.pattern})."
                }
            }
            return null
        }
    }
}
