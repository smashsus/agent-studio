package com.example.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class AgentProfile(
    val id: String,
    val handle: String,
    val name: String,
    val description: String,
    val category: String = "Workspace",
    val systemPrompt: String,
    val preferredModelId: String = "",
    val allowedTools: List<String> = emptyList(), // empty = all enabled global tools
    val disableAllTools: Boolean = false,
    val isBuiltIn: Boolean = false,
    val isInstalled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

class AgentProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _agents = MutableStateFlow<List<AgentProfile>>(emptyList())
    val agents: StateFlow<List<AgentProfile>> = _agents.asStateFlow()

    private val _activeAgentId = MutableStateFlow(DEFAULT_AGENT_ID)
    val activeAgentId: StateFlow<String> = _activeAgentId.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val raw = prefs.getString(KEY_AGENTS_JSON, null)
        val saved = if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<AgentProfile>>(raw) }.getOrDefault(emptyList())
        } else emptyList()

        val merged = if (saved.isEmpty()) {
            defaultCatalog()
        } else {
            val byId = saved.associateBy { it.id }.toMutableMap()
            defaultCatalog().forEach { catalogItem ->
                if (!byId.containsKey(catalogItem.id)) {
                    byId[catalogItem.id] = catalogItem
                }
            }
            byId.values.toList()
        }

        _agents.value = merged
        val savedActive = prefs.getString(KEY_ACTIVE_AGENT_ID, DEFAULT_AGENT_ID) ?: DEFAULT_AGENT_ID
        val validActive = merged.firstOrNull { it.id == savedActive && it.isInstalled }?.id
            ?: merged.firstOrNull { it.isInstalled }?.id
            ?: DEFAULT_AGENT_ID
        _activeAgentId.value = validActive
    }

    private fun persist(list: List<AgentProfile>, activeId: String) {
        _agents.value = list
        _activeAgentId.value = activeId
        prefs.edit()
            .putString(KEY_AGENTS_JSON, json.encodeToString(list))
            .putString(KEY_ACTIVE_AGENT_ID, activeId)
            .apply()
    }

    fun getActiveAgent(): AgentProfile {
        val currentId = _activeAgentId.value
        return _agents.value.firstOrNull { it.id == currentId && it.isInstalled }
            ?: _agents.value.firstOrNull { it.isInstalled }
            ?: defaultCatalog().first()
    }

    fun selectAgent(agentId: String): AgentProfile? {
        val target = _agents.value.firstOrNull { it.id == agentId } ?: return null
        val updatedList = if (!target.isInstalled) {
            _agents.value.map { if (it.id == agentId) it.copy(isInstalled = true) else it }
        } else _agents.value
        persist(updatedList, agentId)
        return updatedList.firstOrNull { it.id == agentId }
    }

    fun installCatalogAgent(agentId: String, activate: Boolean = true): AgentProfile? {
        val target = _agents.value.firstOrNull { it.id == agentId } ?: return null
        val updated = target.copy(isInstalled = true, updatedAt = System.currentTimeMillis())
        val nextList = _agents.value.map { if (it.id == agentId) updated else it }
        val nextActive = if (activate) updated.id else _activeAgentId.value
        persist(nextList, nextActive)
        return updated
    }

    fun uninstallOrDeleteAgent(agentId: String) {
        if (agentId == DEFAULT_AGENT_ID) return
        val target = _agents.value.firstOrNull { it.id == agentId } ?: return
        val nextList = if (target.isBuiltIn) {
            _agents.value.map { if (it.id == agentId) it.copy(isInstalled = false) else it }
        } else {
            _agents.value.filterNot { it.id == agentId }
        }
        val nextActive = if (_activeAgentId.value == agentId) DEFAULT_AGENT_ID else _activeAgentId.value
        persist(nextList, nextActive)
    }

    fun saveOrUpdateAgent(
        existingId: String?,
        name: String,
        handle: String,
        description: String,
        systemPrompt: String,
        preferredModelId: String,
        allowedTools: List<String>,
        disableAllTools: Boolean,
        activate: Boolean = true
    ): AgentProfile {
        val cleanName = name.trim().ifBlank { "Custom Agent" }
        val cleanHandle = normalizeHandle(handle.ifBlank { cleanName })
        val id = existingId?.takeIf { it.isNotBlank() }
            ?: "agent_${UUID.randomUUID().toString().take(8)}"
        val existing = _agents.value.firstOrNull { it.id == id }
        val profile = AgentProfile(
            id = id,
            handle = cleanHandle,
            name = cleanName,
            description = description.trim().ifBlank { "Custom installed workspace agent." },
            category = existing?.category ?: "Custom",
            systemPrompt = systemPrompt.trim().ifBlank {
                "You are $cleanName ($cleanHandle), a focused autonomous assistant."
            },
            preferredModelId = preferredModelId.trim(),
            allowedTools = allowedTools.distinct(),
            disableAllTools = disableAllTools,
            isBuiltIn = existing?.isBuiltIn ?: false,
            isInstalled = true,
            updatedAt = System.currentTimeMillis()
        )
        val nextList = _agents.value.toMutableList().apply {
            val idx = indexOfFirst { it.id == id }
            if (idx >= 0) set(idx, profile) else add(profile)
        }
        val nextActive = if (activate) profile.id else _activeAgentId.value
        persist(nextList, nextActive)
        return profile
    }

    fun installFromSpecText(rawSpec: String): Result<AgentProfile> {
        val trimmed = rawSpec.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Paste an agent JSON or Markdown definition first."))
        }
        if (trimmed.startsWith("{")) {
            return runCatching {
                val parsed = json.decodeFromString<AgentImportJson>(trimmed)
                saveOrUpdateAgent(
                    existingId = null,
                    name = parsed.name.ifBlank { "Imported Agent" },
                    handle = parsed.handle.ifBlank { parsed.name },
                    description = parsed.description,
                    systemPrompt = parsed.systemPrompt.ifBlank { parsed.prompt },
                    preferredModelId = parsed.model,
                    allowedTools = parsed.tools,
                    disableAllTools = parsed.disableTools,
                    activate = true
                )
            }
        }
        // Parse OpenCode-style Markdown frontmatter + prompt body
        val lines = trimmed.lines()
        var name = "Imported Agent"
        var handle = "@imported"
        var desc = ""
        var model = ""
        val tools = mutableListOf<String>()
        var bodyStart = 0
        if (lines.firstOrNull()?.trim() == "---") {
            val endIdx = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIdx >= 0) {
                val frontmatter = lines.subList(1, endIdx + 1)
                frontmatter.forEach { line ->
                    val sep = line.indexOf(':')
                    if (sep > 0) {
                        val k = line.substring(0, sep).trim().lowercase()
                        val v = line.substring(sep + 1).trim().trim('"', '\'')
                        when (k) {
                            "name", "title" -> name = v
                            "handle", "slug", "id" -> handle = v
                            "description" -> desc = v
                            "model" -> model = v
                            "tools" -> tools.addAll(
                                v.removePrefix("[").removeSuffix("]")
                                    .split(",")
                                    .map { it.trim().trim('"', '\'') }
                                    .filter { it.isNotBlank() }
                            )
                        }
                    }
                }
                bodyStart = endIdx + 2
            }
        }
        val promptBody = lines.drop(bodyStart).joinToString("\n").trim().ifBlank { trimmed }
        return Result.success(
            saveOrUpdateAgent(
                existingId = null,
                name = name,
                handle = handle,
                description = desc.ifBlank { "Installed from Markdown definition" },
                systemPrompt = promptBody,
                preferredModelId = model,
                allowedTools = tools,
                disableAllTools = false,
                activate = true
            )
        )
    }

    fun resetToDefaults() {
        persist(defaultCatalog(), DEFAULT_AGENT_ID)
    }

    companion object {
        private const val PREFS_NAME = "agent_studio_profiles"
        private const val KEY_AGENTS_JSON = "agents_json"
        private const val KEY_ACTIVE_AGENT_ID = "active_agent_id"
        const val DEFAULT_AGENT_ID = "build"

        fun normalizeHandle(raw: String): String {
            val cleaned = raw.trim().lowercase()
                .removePrefix("@")
                .replace(Regex("[^a-z0-9_-]+"), "-")
                .trim('-')
                .ifBlank { "agent" }
            return "@$cleaned"
        }

        fun defaultCatalog(): List<AgentProfile> = listOf(
            AgentProfile(
                id = "build",
                handle = "@build",
                name = "General Builder",
                description = "Full-capability autonomous Android workspace agent with shell, web, JS, and files.",
                category = "Core",
                systemPrompt = "You are a versatile autonomous Android agent. Plan cleanly, invoke tools only when needed, verify outputs, and report results clearly.",
                allowedTools = emptyList(),
                isBuiltIn = true,
                isInstalled = true
            ),
            AgentProfile(
                id = "code",
                handle = "@code",
                name = "Code & Shell Engineer",
                description = "Developer-focused agent tuned for local shell inspection, scripting, JS sandbox & workspace files.",
                category = "Engineering",
                systemPrompt = "You are a pragmatic systems and software engineer running inside Android's workspace shell and JS sandbox. Inspect files first, run concise shell/JS verification commands, and return exact paths and verified outputs.",
                allowedTools = listOf(
                    "run_shell",
                    "run_javascript",
                    "create_file",
                    "read_document",
                    "list_workspace_files",
                    "generate_html_app",
                    "save_memory"
                ),
                isBuiltIn = true,
                isInstalled = true
            ),
            AgentProfile(
                id = "research",
                handle = "@research",
                name = "Web Research Analyst",
                description = "Deep multi-source web researcher that synthesizes verified Markdown briefs and saves reports.",
                category = "Research",
                systemPrompt = "You are a rigorous technical research analyst. Search multiple sources, fetch primary pages, synthesize evidence with clear citations, and save structured Markdown reports when helpful.",
                allowedTools = listOf(
                    "web_search",
                    "fetch_webpage",
                    "generate_report",
                    "read_document",
                    "analyze_pdf",
                    "save_memory"
                ),
                isBuiltIn = true,
                isInstalled = false
            ),
            AgentProfile(
                id = "studio",
                handle = "@studio",
                name = "HTML5 Artifact Studio",
                description = "Builds self-contained interactive HTML/CSS/JS apps and previews them in the sandbox.",
                category = "Builder",
                systemPrompt = "You specialize in crafting polished, self-contained HTML5/CSS/JS micro-apps with clean typography and responsive layouts using generate_html_app.",
                allowedTools = listOf(
                    "generate_html_app",
                    "create_file",
                    "run_javascript",
                    "list_workspace_files",
                    "read_document"
                ),
                isBuiltIn = true,
                isInstalled = false
            ),
            AgentProfile(
                id = "android",
                handle = "@android",
                name = "Device & UI Operator",
                description = "Android automation agent for launching apps, reading screen state, notifications & clipboard.",
                category = "Android",
                systemPrompt = "You are an Android device assistant. Respect user consent and privacy rules, inspect screen elements cleanly before acting, and confirm actions clearly.",
                allowedTools = listOf(
                    "accessibility_action",
                    "open_app",
                    "open_url",
                    "clipboard_action",
                    "send_notification",
                    "manage_task_list",
                    "share_content"
                ),
                isBuiltIn = true,
                isInstalled = false
            ),
            AgentProfile(
                id = "chat",
                handle = "@chat",
                name = "Direct Chat (No Tools)",
                description = "Lightweight direct conversation or role-play mode with zero tool-calling overhead.",
                category = "Chat",
                systemPrompt = "Respond directly and naturally in conversation without invoking external tools.",
                disableAllTools = true,
                isBuiltIn = true,
                isInstalled = false
            )
        )
    }
}

@Serializable
private data class AgentImportJson(
    val name: String = "",
    val handle: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val prompt: String = "",
    val model: String = "",
    val tools: List<String> = emptyList(),
    val disableTools: Boolean = false
)
