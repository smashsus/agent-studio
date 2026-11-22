package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agentSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "deepseek_agent_settings"
)

enum class AutonomyLevel(val label: String, val description: String) {
    AUTONOMOUS(
        "Autonomous",
        "Executes all permitted tools in multi-step loops without pausing."
    ),
    BALANCED(
        "Balanced",
        "Auto-runs safe read/search tools; requests approval for file writes, Intents, or UI automation."
    ),
    STRICT(
        "Strict",
        "Requires explicit human approval before every tool execution step."
    )
}

data class AgentSettings(
    val autonomyLevel: AutonomyLevel = AutonomyLevel.AUTONOMOUS,
    val modelId: String = "",
    val customBaseUrl: String = "",
    val retryLimit: Int = 2,
    val timeoutSeconds: Int = 30,
    val maxSteps: Int = 10,
    val themeMode: ThemeMode = ThemeMode.WHITE,
    val restrictNetworkDomains: Boolean = false,
    val allowedDomainsCsv: String = "duckduckgo.com,en.wikipedia.org,github.com,arxiv.org",
    val longTermMemoryEnabled: Boolean = true,
    val autoSummarizeTasks: Boolean = true,
    val accessibilityUserConsent: Boolean = false,
    val disabledTools: Set<String> = emptySet()
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val AUTONOMY_LEVEL = stringPreferencesKey("autonomy_level")
        val MODEL_ID = stringPreferencesKey("model_id")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val RETRY_LIMIT = intPreferencesKey("retry_limit")
        val TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        val MAX_STEPS = intPreferencesKey("max_steps")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RESTRICT_DOMAINS = booleanPreferencesKey("restrict_domains")
        val ALLOWED_DOMAINS = stringPreferencesKey("allowed_domains")
        val LONG_TERM_MEMORY = booleanPreferencesKey("long_term_memory")
        val AUTO_SUMMARIZE_TASKS = booleanPreferencesKey("auto_summarize_tasks")
        val ACCESSIBILITY_CONSENT = booleanPreferencesKey("accessibility_consent")
        val DISABLED_TOOLS = stringSetPreferencesKey("disabled_tools")
    }

    val settings: Flow<AgentSettings> = context.agentSettingsDataStore.data.map { prefs ->
        val autonomy = prefs[Keys.AUTONOMY_LEVEL]
            ?.let { runCatching { AutonomyLevel.valueOf(it) }.getOrNull() }
            ?: AutonomyLevel.AUTONOMOUS
        val theme = ThemeMode.fromStored(prefs[Keys.THEME_MODE])
        AgentSettings(
            autonomyLevel = autonomy,
            modelId = prefs[Keys.MODEL_ID].orEmpty(),
            customBaseUrl = prefs[Keys.CUSTOM_BASE_URL].orEmpty(),
            retryLimit = (prefs[Keys.RETRY_LIMIT] ?: 2).coerceIn(0, 5),
            timeoutSeconds = (prefs[Keys.TIMEOUT_SECONDS] ?: 30).coerceIn(10, 120),
            maxSteps = (prefs[Keys.MAX_STEPS] ?: 10).coerceIn(2, 25),
            themeMode = theme,
            restrictNetworkDomains = prefs[Keys.RESTRICT_DOMAINS] ?: false,
            allowedDomainsCsv = prefs[Keys.ALLOWED_DOMAINS]
                ?: "duckduckgo.com,en.wikipedia.org,github.com,arxiv.org",
            longTermMemoryEnabled = prefs[Keys.LONG_TERM_MEMORY] ?: true,
            autoSummarizeTasks = prefs[Keys.AUTO_SUMMARIZE_TASKS] ?: true,
            accessibilityUserConsent = prefs[Keys.ACCESSIBILITY_CONSENT] ?: false,
            disabledTools = prefs[Keys.DISABLED_TOOLS] ?: emptySet()
        )
    }

    suspend fun setAutonomyLevel(level: AutonomyLevel) {
        context.agentSettingsDataStore.edit { it[Keys.AUTONOMY_LEVEL] = level.name }
    }

    suspend fun setModelConfig(modelId: String, customBaseUrl: String) {
        context.agentSettingsDataStore.edit {
            it[Keys.MODEL_ID] = modelId.trim()
            it[Keys.CUSTOM_BASE_URL] = customBaseUrl.trim()
        }
    }

    suspend fun setExecutionLimits(retryLimit: Int, timeoutSeconds: Int, maxSteps: Int) {
        context.agentSettingsDataStore.edit {
            it[Keys.RETRY_LIMIT] = retryLimit.coerceIn(0, 5)
            it[Keys.TIMEOUT_SECONDS] = timeoutSeconds.coerceIn(10, 120)
            it[Keys.MAX_STEPS] = maxSteps.coerceIn(2, 25)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.agentSettingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setNetworkPolicy(restrict: Boolean, allowedDomainsCsv: String) {
        context.agentSettingsDataStore.edit {
            it[Keys.RESTRICT_DOMAINS] = restrict
            it[Keys.ALLOWED_DOMAINS] = allowedDomainsCsv
        }
    }

    suspend fun setMemoryPreferences(longTermEnabled: Boolean, autoSummarize: Boolean) {
        context.agentSettingsDataStore.edit {
            it[Keys.LONG_TERM_MEMORY] = longTermEnabled
            it[Keys.AUTO_SUMMARIZE_TASKS] = autoSummarize
        }
    }

    suspend fun setAccessibilityConsent(consented: Boolean) {
        context.agentSettingsDataStore.edit { it[Keys.ACCESSIBILITY_CONSENT] = consented }
    }

    suspend fun setToolEnabled(toolName: String, enabled: Boolean) {
        context.agentSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_TOOLS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.remove(toolName) else current.add(toolName)
            prefs[Keys.DISABLED_TOOLS] = current
        }
    }

    suspend fun resetDefaults() {
        context.agentSettingsDataStore.edit { it.clear() }
    }
}
