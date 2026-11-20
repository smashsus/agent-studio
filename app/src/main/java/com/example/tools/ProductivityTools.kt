package com.example.tools

import com.example.database.AppDatabase
import com.example.database.SubtaskEntity
import com.example.memory.MemoryLayer
import com.example.memory.MemoryManager
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class ManageTaskListTool(private val db: AppDatabase) : AgentTool {
    override val definition = ToolDefinition(
        name = "manage_task_list",
        category = "Productivity",
        description = "Creates or updates a subtask in the active Task Plan and Task Memory.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("plan_id", "integer", "Active TaskPlan ID.", required = false),
            ToolParameter("title", "string", "Subtask title."),
            ToolParameter("status", "string", "PENDING, RUNNING, COMPLETED, FAILED.", required = false),
            ToolParameter("notes", "string", "Optional result or progress summary.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing subtask title.")
        val status = args["status"]?.jsonPrimitive?.contentOrNull?.uppercase()?.trim() ?: "COMPLETED"
        val notes = args["notes"]?.jsonPrimitive?.contentOrNull?.trim()
        val planId = args["plan_id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

        val dao = db.taskPlanDao()
        if (planId != null) {
            val existing = dao.getSubtasksOnce(planId).firstOrNull {
                it.title.equals(title, ignoreCase = true)
            }
            if (existing != null) {
                dao.updateSubtaskStatus(existing.id, status, notes)
                return ToolResult(true, "Updated subtask '${existing.title}' -> $status")
            }
            val nextIdx = dao.getSubtasksOnce(planId).size
            dao.insertSubtasks(
                listOf(
                    SubtaskEntity(
                        planId = planId,
                        orderIndex = nextIdx,
                        title = title,
                        description = notes ?: title,
                        status = status,
                        resultSummary = notes
                    )
                )
            )
            return ToolResult(true, "Added subtask '$title' ($status) to plan #$planId.")
        }

        return ToolResult(true, "Recorded task item: $title ($status).")
    }
}

class SaveMemoryTool(private val memoryManager: MemoryManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "save_memory",
        category = "Productivity",
        description = "Stores a concise fact or preference into Task Memory or Long-Term Memory.",
        riskLevel = ToolRiskLevel.SAFE,
        parameters = listOf(
            ToolParameter("key", "string", "Short descriptive memory key."),
            ToolParameter("content", "string", "Information to remember."),
            ToolParameter("layer", "string", "TASK or LONG_TERM.", required = false),
            ToolParameter("category", "string", "Category tag (Preference, Research, Project).", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing memory key.")
        val content = args["content"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing memory content.")
        val layer = args["layer"]?.jsonPrimitive?.contentOrNull?.uppercase()?.trim()
            ?.takeIf { it == MemoryLayer.TASK || it == MemoryLayer.LONG_TERM }
            ?: MemoryLayer.TASK
        val category = args["category"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Agent Note"

        if (layer == MemoryLayer.LONG_TERM && !settings.longTermMemoryEnabled) {
            return ToolResult(false, "", error = "Long-term memory is disabled in Settings.")
        }

        val id = memoryManager.saveMemory(
            layer = layer,
            keyName = key,
            content = content,
            category = category,
            userApproved = layer == MemoryLayer.TASK || settings.longTermMemoryEnabled
        )
        return ToolResult(true, "Saved memory #$id [$layer]: $key = $content")
    }
}
