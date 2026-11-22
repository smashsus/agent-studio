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
data class SwarmMemberSlot(
    val slotId: String = UUID.randomUUID().toString().take(8),
    val agentId: String,
    val providerId: String = "",
    val modelIdOverride: String = "",
    val roleNote: String = ""
)

@Serializable
data class SwarmConfig(
    val enabled: Boolean = false,
    val maxCritiqueRounds: Int = 2,
    val members: List<SwarmMemberSlot> = defaultMembers()
) {
    companion object {
        fun defaultMembers(): List<SwarmMemberSlot> = listOf(
            SwarmMemberSlot(
                slotId = "slot_arch",
                agentId = "build",
                roleNote = "Primary Planner & Integrator"
            ),
            SwarmMemberSlot(
                slotId = "slot_eng",
                agentId = "code",
                roleNote = "Technical Verifier & Edge-Case Auditor"
            ),
            SwarmMemberSlot(
                slotId = "slot_res",
                agentId = "research",
                roleNote = "Fact Checker & Completeness Reviewer"
            )
        )
    }
}

class SwarmConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<SwarmConfig> = _config.asStateFlow()

    private fun load(): SwarmConfig {
        val raw = prefs.getString(KEY_SWARM_JSON, null) ?: return SwarmConfig()
        return runCatching {
            val decoded = json.decodeFromString<SwarmConfig>(raw)
            if (decoded.members.isEmpty()) decoded.copy(members = SwarmConfig.defaultMembers()) else decoded
        }.getOrDefault(SwarmConfig())
    }

    private fun persist(next: SwarmConfig) {
        _config.value = next
        prefs.edit().putString(KEY_SWARM_JSON, json.encodeToString(next)).apply()
    }

    fun setEnabled(enabled: Boolean) {
        val cur = _config.value
        val members = if (enabled && cur.members.size < 2) SwarmConfig.defaultMembers() else cur.members
        persist(cur.copy(enabled = enabled, members = members))
    }

    fun setMaxCritiqueRounds(rounds: Int) {
        persist(_config.value.copy(maxCritiqueRounds = rounds.coerceIn(1, 3)))
    }

    fun addMember(
        agentId: String,
        providerId: String = "",
        modelIdOverride: String = "",
        roleNote: String = ""
    ) {
        val cur = _config.value
        if (cur.members.size >= MAX_SWARM_AGENTS) return
        val slot = SwarmMemberSlot(
            slotId = "slot_${UUID.randomUUID().toString().take(6)}",
            agentId = agentId,
            providerId = providerId.trim(),
            modelIdOverride = modelIdOverride.trim(),
            roleNote = roleNote.trim()
        )
        persist(cur.copy(members = cur.members + slot))
    }

    fun setTargetMemberCount(count: Int, availableAgentIds: List<String>) {
        val target = count.coerceIn(2, MAX_SWARM_AGENTS)
        val cur = _config.value
        val pool = availableAgentIds.ifEmpty { listOf("build", "code", "research") }
        val updated = cur.members.toMutableList()
        while (updated.size < target) {
            val nextAgent = pool[updated.size % pool.size]
            updated.add(
                SwarmMemberSlot(
                    slotId = "slot_${UUID.randomUUID().toString().take(6)}",
                    agentId = nextAgent,
                    roleNote = "Agent #${updated.size + 1}"
                )
            )
        }
        while (updated.size > target) {
            updated.removeAt(updated.lastIndex)
        }
        persist(cur.copy(members = updated))
    }

    fun updateMember(
        slotId: String,
        agentId: String,
        providerId: String,
        modelIdOverride: String,
        roleNote: String
    ) {
        val cur = _config.value
        val updated = cur.members.map { slot ->
            if (slot.slotId == slotId) {
                slot.copy(
                    agentId = agentId,
                    providerId = providerId.trim(),
                    modelIdOverride = modelIdOverride.trim(),
                    roleNote = roleNote.trim()
                )
            } else slot
        }
        persist(cur.copy(members = updated))
    }

    fun removeMember(slotId: String) {
        val cur = _config.value
        if (cur.members.size <= 1) return
        persist(cur.copy(members = cur.members.filterNot { it.slotId == slotId }))
    }

    fun resetDefaults() {
        persist(SwarmConfig())
    }

    companion object {
        private const val PREFS_NAME = "agent_studio_swarm"
        private const val KEY_SWARM_JSON = "swarm_config_json"
        const val MAX_SWARM_AGENTS = 8
    }
}
