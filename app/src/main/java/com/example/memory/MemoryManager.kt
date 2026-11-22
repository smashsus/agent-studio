package com.example.memory

import com.example.database.AppDatabase
import com.example.database.MemoryEntryEntity
import com.example.database.MessageEntity
import com.example.database.SubtaskEntity
import com.example.database.TaskPlanEntity
import kotlinx.coroutines.flow.Flow

object MemoryLayer {
    const val TASK = "TASK"
    const val LONG_TERM = "LONG_TERM"
}

class MemoryManager(private val db: AppDatabase) {
    private val memoryDao = db.memoryDao()
    private val messageDao = db.messageDao()
    private val taskPlanDao = db.taskPlanDao()

    val taskMemories: Flow<List<MemoryEntryEntity>> = memoryDao.observeByLayer(MemoryLayer.TASK)
    val longTermMemories: Flow<List<MemoryEntryEntity>> = memoryDao.observeByLayer(MemoryLayer.LONG_TERM)
    val allPlans: Flow<List<TaskPlanEntity>> = taskPlanDao.observeAllPlans()

    fun observeShortTermMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeMessages(conversationId)

    suspend fun getShortTermContext(conversationId: Long, limit: Int = 20): List<MessageEntity> {
        val all = messageDao.getMessagesOnce(conversationId)
        return if (all.size <= limit) all else all.takeLast(limit)
    }

    suspend fun getApprovedLongTermMemories(): List<MemoryEntryEntity> =
        memoryDao.getApprovedByLayer(MemoryLayer.LONG_TERM)

    suspend fun getActiveTaskMemories(): List<MemoryEntryEntity> =
        memoryDao.getApprovedByLayer(MemoryLayer.TASK)

    suspend fun buildSystemMemoryContext(includeLongTerm: Boolean): String {
        val sb = StringBuilder()
        val taskEntries = getActiveTaskMemories()
            .distinctBy { "${it.keyName.trim().lowercase()}::${it.content.trim().lowercase()}" }
        if (taskEntries.isNotEmpty()) {
            sb.appendLine("## Active Task Memory")
            taskEntries.take(8).forEach { entry ->
                sb.appendLine("- [${entry.category}] ${entry.keyName}: ${entry.content}")
            }
        }
        if (includeLongTerm) {
            val longEntries = getApprovedLongTermMemories()
                .distinctBy { "${it.keyName.trim().lowercase()}::${it.content.trim().lowercase()}" }
            if (longEntries.isNotEmpty()) {
                sb.appendLine("## Approved Long-Term Memory")
                longEntries.take(12).forEach { entry ->
                    sb.appendLine("- [${entry.category}] ${entry.keyName}: ${entry.content}")
                }
            }
        }
        return sb.toString().trim()
    }

    suspend fun saveMemory(
        layer: String,
        keyName: String,
        content: String,
        category: String = "General",
        userApproved: Boolean = true
    ): Long {
        val now = System.currentTimeMillis()
        return memoryDao.insert(
            MemoryEntryEntity(
                layer = layer,
                keyName = keyName.trim(),
                content = content.trim(),
                category = category.trim(),
                userApproved = userApproved,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateMemory(entry: MemoryEntryEntity) {
        memoryDao.update(entry.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteById(id)
    }

    suspend fun clearLayer(layer: String) {
        memoryDao.clearLayer(layer)
    }

    suspend fun clearAllMemories() {
        memoryDao.deleteAll()
    }

    fun observeLatestPlan(conversationId: Long): Flow<TaskPlanEntity?> =
        taskPlanDao.observeLatestPlan(conversationId)

    fun observeSubtasks(planId: Long): Flow<List<SubtaskEntity>> =
        taskPlanDao.observeSubtasks(planId)
}
