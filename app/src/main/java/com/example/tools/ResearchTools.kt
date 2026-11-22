package com.example.tools

import com.example.permissions.PermissionManager
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchTool(
    private val permissionManager: PermissionManager
) : AgentTool {
    override val definition = ToolDefinition(
        name = "web_search",
        category = "Research",
        description = "Searches permitted public web sources and returns ranked titles, URLs, and snippets.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("query", "string", "Search query keywords."),
            ToolParameter("max_results", "integer", "Number of results (1-8).", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult =
        withContext(Dispatchers.IO) {
            val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: return@withContext ToolResult(false, "", error = "Missing query argument.")
            val maxResults = args["max_results"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 8) ?: 5

            val encoded = URLEncoder.encode(query, "UTF-8")
            val ddgUrl = "https://html.duckduckgo.com/html/?q=$encoded"
            val wikiUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=$maxResults&namespace=0&format=json"

            val client = OkHttpClient.Builder()
                .connectTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            val ddgPermitted = permissionManager.isUrlPermitted(ddgUrl, settings).isSuccess
            if (ddgPermitted) {
                val ddgResults = runCatching { queryDuckDuckGo(client, ddgUrl, maxResults) }.getOrNull()
                if (!ddgResults.isNullOrEmpty()) {
                    return@withContext ToolResult(true, formatSearchResults(query, ddgResults))
                }
            }

            // Fallback to Wikipedia OpenSearch if permitted
            if (permissionManager.isUrlPermitted(wikiUrl, settings).isSuccess) {
                val wikiResults = runCatching { queryWikipedia(client, wikiUrl) }.getOrNull()
                if (!wikiResults.isNullOrEmpty()) {
                    return@withContext ToolResult(true, formatSearchResults(query, wikiResults))
                }
            }

            ToolResult(
                success = false,
                output = "",
                error = "No permitted search sources returned results (check network connectivity or domain allowlist)."
            )
        }

    private fun queryDuckDuckGo(client: OkHttpClient, url: String, maxResults: Int): List<SearchHit> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AgentStudio/1.0")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val html = resp.body?.string() ?: return emptyList()
            val linkRegex = Regex(
                """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val snippetRegex = Regex(
                """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>|<div[^>]*class="result__snippet"[^>]*>(.*?)</div>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val links = linkRegex.findAll(html).toList()
            val snippets = snippetRegex.findAll(html).toList()
            val hits = mutableListOf<SearchHit>()
            for (i in 0 until minOf(links.size, maxResults)) {
                val rawHref = cleanHtml(links[i].groupValues[1])
                val cleanUrl = extractDuckDuckGoTarget(rawHref)
                val title = cleanHtml(links[i].groupValues[2])
                val snippet = snippets.getOrNull(i)?.let {
                    cleanHtml(it.groupValues[1].ifEmpty { it.groupValues[2] })
                } ?: ""
                if (title.isNotBlank() && cleanUrl.startsWith("http")) {
                    hits.add(SearchHit(title, cleanUrl, snippet))
                }
            }
            return hits
        }
    }

    private fun queryWikipedia(client: OkHttpClient, url: String): List<SearchHit> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AgentStudioAndroid/1.0")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            val arr = Json.parseToJsonElement(body).jsonArray
            if (arr.size < 4) return emptyList()
            val titles = arr[1].jsonArray
            val descs = arr[2].jsonArray
            val urls = arr[3].jsonArray
            val hits = mutableListOf<SearchHit>()
            for (i in 0 until minOf(titles.size, urls.size)) {
                hits.add(
                    SearchHit(
                        title = titles[i].jsonPrimitive.content,
                        url = urls[i].jsonPrimitive.content,
                        snippet = descs.getOrNull(i)?.jsonPrimitive?.content ?: ""
                    )
                )
            }
            return hits
        }
    }

    private fun extractDuckDuckGoTarget(href: String): String {
        val uddgMarker = "uddg="
        val idx = href.indexOf(uddgMarker)
        return if (idx >= 0) {
            val encoded = href.substring(idx + uddgMarker.length).substringBefore("&")
            runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(href)
        } else if (href.startsWith("//")) {
            "https:$href"
        } else {
            href
        }
    }

    private fun formatSearchResults(query: String, hits: List<SearchHit>): String = buildString {
        appendLine("Search Results for \"$query\" (${hits.size} sources):")
        hits.forEachIndexed { idx, hit ->
            appendLine("${idx + 1}. ${hit.title}")
            appendLine("   URL: ${hit.url}")
            if (hit.snippet.isNotBlank()) appendLine("   Summary: ${hit.snippet}")
        }
    }

    private data class SearchHit(val title: String, val url: String, val snippet: String)
}

class FetchWebpageTool(
    private val permissionManager: PermissionManager
) : AgentTool {
    override val definition = ToolDefinition(
        name = "fetch_webpage",
        category = "Research",
        description = "Navigates to an HTTPS webpage and extracts clean readable text and metadata.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("url", "string", "HTTPS URL to fetch and extract.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult =
        withContext(Dispatchers.IO) {
            val rawUrl = args["url"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: return@withContext ToolResult(false, "", error = "Missing url argument.")

            val permitted = permissionManager.isUrlPermitted(rawUrl, settings)
            if (permitted.isFailure) {
                return@withContext ToolResult(
                    false,
                    "",
                    error = permitted.exceptionOrNull()?.message ?: "URL not permitted."
                )
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AgentStudio/1.0")
                .get()
                .build()

            runCatching {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext ToolResult(false, "", error = "HTTP ${resp.code} fetching $rawUrl")
                    }
                    val rawHtml = resp.body?.string()?.take(250_000) ?: ""
                    val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(rawHtml)?.groupValues?.getOrNull(1)?.let { cleanHtml(it) } ?: rawUrl
                    val stripped = rawHtml
                        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                        .replace(Regex("<noscript[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), " ")
                    val text = cleanHtml(stripped).take(8500)
                    ToolResult(true, "Page Title: $title\nURL: $rawUrl\n\n$text")
                }
            }.getOrElse { err ->
                ToolResult(false, "", error = err.message ?: "Failed to fetch webpage.")
            }
        }
}

class GenerateReportTool(private val fileManager: WorkspaceFileManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "generate_report",
        category = "Research",
        description = "Generates a structured research report (.md or .pdf) inside AgentWorkspace/reports.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("title", "string", "Report title."),
            ToolParameter("markdown_body", "string", "Full structured report content in Markdown."),
            ToolParameter("format", "string", "Output format: md or pdf.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Research_Report"
        val body = args["markdown_body"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing markdown_body.")
        val format = args["format"]?.jsonPrimitive?.contentOrNull?.lowercase()?.trim() ?: "md"
        val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40)
            .ifEmpty { "research_report" }

        return runCatching {
            if (format == "pdf") {
                val plain = body.replace(Regex("[#*]"), "")
                val file = fileManager.writePdfFile("reports", "$slug.pdf", title, plain)
                ToolResult(
                    success = true,
                    output = "Saved PDF report '${file.name}' (${file.length()} bytes) in AgentWorkspace/reports.",
                    artifactPath = file.absolutePath,
                    artifactMimeType = "application/pdf"
                )
            } else {
                val content = "# $title\n\n$body"
                val file = fileManager.writeTextFile("reports", "$slug.md", content)
                ToolResult(
                    success = true,
                    output = "Saved Markdown report '${file.name}' (${file.length()} bytes) in AgentWorkspace/reports.",
                    artifactPath = file.absolutePath,
                    artifactMimeType = "text/markdown"
                )
            }
        }.getOrElse { err ->
            ToolResult(false, "", error = err.message ?: "Failed to write report.")
        }
    }
}

private fun cleanHtml(input: String): String {
    return input
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
