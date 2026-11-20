package com.example.execution

import com.example.database.AppDatabase
import com.example.database.SubtaskEntity
import com.example.database.TaskPlanEntity

class TaskPlanner(private val db: AppDatabase) {
    private val dao = db.taskPlanDao()

    suspend fun createPlanForRequest(conversationId: Long, userGoal: String): Pair<Long, List<SubtaskEntity>> {
        val planId = dao.insertPlan(
            TaskPlanEntity(
                conversationId = conversationId,
                goal = userGoal.take(240),
                status = "RUNNING"
            )
        )
        val steps = decomposeGoal(userGoal).mapIndexed { index, (title, desc) ->
            SubtaskEntity(
                planId = planId,
                orderIndex = index,
                title = title,
                description = desc,
                status = if (index == 0) "RUNNING" else "PENDING"
            )
        }
        dao.insertSubtasks(steps)
        return planId to dao.getSubtasksOnce(planId)
    }

    suspend fun advancePlanStep(planId: Long, stepIndex: Int, summary: String?) {
        val subtasks = dao.getSubtasksOnce(planId)
        val current = subtasks.getOrNull(stepIndex)
        if (current != null && current.status != "COMPLETED") {
            dao.updateSubtaskStatus(current.id, "COMPLETED", summary?.take(160))
        }
        val next = subtasks.getOrNull(stepIndex + 1)
        if (next != null && next.status == "PENDING") {
            dao.updateSubtaskStatus(next.id, "RUNNING", null)
        }
    }

    suspend fun completePlan(planId: Long, finalStatus: String) {
        val subtasks = dao.getSubtasksOnce(planId)
        subtasks.forEach { st ->
            if (st.status == "RUNNING" || st.status == "PENDING") {
                val target = if (finalStatus == "COMPLETED") "COMPLETED" else finalStatus
                dao.updateSubtaskStatus(st.id, target, st.resultSummary)
            }
        }
        dao.updatePlanStatus(planId, finalStatus)
    }

    private fun decomposeGoal(goal: String): List<Pair<String, String>> {
        val lower = goal.lowercase()
        return when {
            lower.contains("research") || lower.contains("report") || lower.contains("compare") -> listOf(
                "Understand Request & Scope" to "Parse goal, constraints, and active memory context.",
                "Search & Gather Sources" to "Query permitted web sources and extract key facts.",
                "Synthesize & Verify" to "Cross-check findings and structure coherent insights.",
                "Produce Final Output" to "Deliver response and save structured report artifacts if requested."
            )
            lower.contains("pdf") || lower.contains("document") || lower.contains("file") -> listOf(
                "Inspect Target Document" to "Read user-authorized URI or workspace document.",
                "Extract & Analyze Content" to "Parse structure, pages, and key takeaways.",
                "Generate Output Artifact" to "Summarize findings or create requested output file."
            )
            lower.contains("html") || lower.contains("code") || lower.contains("debug") || lower.contains("javascript") -> listOf(
                "Analyze Coding Objective" to "Identify language, constraints, and target behavior.",
                "Build & Sandbox Test" to "Generate code/HTML and verify in isolated JS sandbox.",
                "Deliver Verified Solution" to "Present explanation, code, and previewable artifact."
            )
            else -> listOf(
                "Analyze Objective" to "Determine required context, permissions, and tools.",
                "Execute Tool Steps" to "Run validated Android/research tools autonomously.",
                "Deliver Final Response" to "Summarize outcomes and persist conversation memory."
            )
        }
    }
}
