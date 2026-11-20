package com.example.tools

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class RunJavaScriptTool(private val context: Context) : AgentTool {
    override val definition = ToolDefinition(
        name = "run_javascript",
        category = "Coding",
        description = "Executes JavaScript inside an isolated Android WebView sandbox (no file/network/shell access).",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("code", "string", "JavaScript source code to evaluate.")
        )
    )

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val code = args["code"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing JavaScript code.")
        if (code.length > 30_000) {
            return ToolResult(false, "", error = "JavaScript payload exceeds 30KB sandbox limit.")
        }

        return withContext(Dispatchers.Main) {
            val logs = mutableListOf<String>()
            val resultDeferred = CompletableDeferred<String>()
            val webView = WebView(context.applicationContext).apply {
                this.settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            logs.add("[${it.messageLevel()}] ${it.message()}")
                        }
                        return true
                    }
                }
            }

            try {
                val wrapped = """
                    (function() {
                      try {
                        var __out = eval(${Json.encodeToString(kotlinx.serialization.serializer<String>(), code)});
                        return JSON.stringify({ ok: true, value: String(__out) });
                      } catch (e) {
                        return JSON.stringify({ ok: false, error: String(e) });
                      }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(wrapped) { raw ->
                    if (!resultDeferred.isCompleted) {
                        resultDeferred.complete(raw ?: "null")
                    }
                }

                val evaluated = withTimeoutOrNull(8_000L) { resultDeferred.await() }
                if (evaluated == null) {
                    ToolResult(false, "", error = "JavaScript execution timed out after 8 seconds.")
                } else {
                    val unescaped = runCatching {
                        Json.decodeFromString<String>(evaluated)
                    }.getOrDefault(evaluated)
                    val logText = if (logs.isNotEmpty()) "\nConsole:\n${logs.joinToString("\n")}" else ""
                    ToolResult(true, "Sandbox Result: $unescaped$logText")
                }
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }
}

class GenerateHtmlAppTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "generate_html_app",
        category = "Coding",
        description = "Generates a standalone interactive HTML/CSS/JS mini-application and saves it for live sandboxed preview.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("filename", "string", "HTML filename (e.g. calculator.html)."),
            ToolParameter("html_content", "string", "Complete self-contained HTML5 document.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val rawName = args["filename"]?.jsonPrimitive?.contentOrNull?.trim() ?: "app.html"
        val filename = if (rawName.endsWith(".html", true)) rawName else "$rawName.html"
        val html = args["html_content"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult(false, "", error = "Missing html_content.")

        return runCatching {
            val file = fileManager.writeTextFile("code", filename, html)
            ToolResult(
                success = true,
                output = "Generated HTML app '${file.name}' (${file.length()} bytes). Tap Preview to run in sandbox.",
                artifactPath = file.absolutePath,
                artifactMimeType = "text/html",
                htmlPreviewContent = html
            )
        }.getOrElse { err ->
            ToolResult(false, "", error = err.message ?: "Failed to save HTML app.")
        }
    }
}
