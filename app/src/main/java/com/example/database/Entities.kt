package com.example.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // USER, ASSISTANT, TOOL, SYSTEM
    val content: String,
    val toolName: String? = null,
    val toolArgumentsJson: String? = null,
    val toolResultJson: String? = null,
    val attachmentUri: String? = null,
    val attachmentName: String? = null,
    val artifactPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_plans",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class TaskPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val goal: String,
    val status: String, // PLANNING, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId")]
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val orderIndex: Int,
    val title: String,
    val description: String,
    val status: String, // PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    val resultSummary: String? = null
)

@Entity(tableName = "memory_entries")
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val layer: String, // TASK, LONG_TERM
    val keyName: String,
    val content: String,
    val category: String,
    val userApproved: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "execution_logs",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val stepIndex: Int,
    val toolName: String,
    val argumentsJson: String,
    val status: String, // RUNNING, SUCCESS, FAILED, CANCELLED, DENIED
    val resultSummary: String,
    val durationMs: Long = 0,
    val retryCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
