package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ConversationSummary(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val messageCount: Int,
    val lastMessagePreview: String?
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT c.id, c.title, c.createdAt, c.updatedAt, c.isPinned,
               (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id) AS messageCount,
               (SELECT m2.content FROM messages m2 WHERE m2.conversationId = c.id ORDER BY m2.timestamp DESC, m2.id DESC LIMIT 1) AS lastMessagePreview
        FROM conversations c
        ORDER BY c.isPinned DESC, c.updatedAt DESC
        """
    )
    fun observeConversationSummaries(): Flow<List<ConversationSummary>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesOnce(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: Long)
}

@Dao
interface TaskPlanDao {
    @Query("SELECT * FROM task_plans WHERE conversationId = :conversationId ORDER BY id DESC LIMIT 1")
    fun observeLatestPlan(conversationId: Long): Flow<TaskPlanEntity?>

    @Query("SELECT * FROM task_plans ORDER BY updatedAt DESC")
    fun observeAllPlans(): Flow<List<TaskPlanEntity>>

    @Query("SELECT * FROM subtasks WHERE planId = :planId ORDER BY orderIndex ASC")
    fun observeSubtasks(planId: Long): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getSubtasksOnce(planId: Long): List<SubtaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: TaskPlanEntity): Long

    @Query("UPDATE task_plans SET status = :status, updatedAt = :updatedAt WHERE id = :planId")
    suspend fun updatePlanStatus(planId: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Query("UPDATE subtasks SET status = :status, resultSummary = :resultSummary WHERE id = :subtaskId")
    suspend fun updateSubtaskStatus(subtaskId: Long, status: String, resultSummary: String? = null)

    @Query("DELETE FROM task_plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE layer = :layer ORDER BY updatedAt DESC")
    fun observeByLayer(layer: String): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE layer = :layer AND userApproved = 1 ORDER BY updatedAt DESC")
    suspend fun getApprovedByLayer(layer: String): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MemoryEntryEntity): Long

    @Update
    suspend fun update(entry: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memory_entries WHERE layer = :layer")
    suspend fun clearLayer(layer: String)

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAll()
}

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs WHERE conversationId = :conversationId ORDER BY timestamp DESC, id DESC LIMIT 100")
    fun observeLogsForConversation(conversationId: Long): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun observeRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecutionLogEntity): Long

    @Query("UPDATE execution_logs SET status = :status, resultSummary = :resultSummary, durationMs = :durationMs, retryCount = :retryCount WHERE id = :id")
    suspend fun updateLog(
        id: Long,
        status: String,
        resultSummary: String,
        durationMs: Long,
        retryCount: Int
    )

    @Query("DELETE FROM execution_logs")
    suspend fun deleteAll()
}
