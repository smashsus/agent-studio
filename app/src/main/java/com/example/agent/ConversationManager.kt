package com.example.agent

import com.example.database.AppDatabase
import com.example.database.ConversationEntity
import com.example.database.ConversationSummary
import com.example.database.MessageEntity
import kotlinx.coroutines.flow.Flow

class ConversationManager(private val db: AppDatabase) {
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    val conversations: Flow<List<ConversationEntity>> = conversationDao.observeConversations()
    val conversationSummaries: Flow<List<ConversationSummary>> = conversationDao.observeConversationSummaries()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeMessages(conversationId)

    suspend fun ensureDefaultConversation(): Long {
        return createConversation("New Agent Mission")
    }

    suspend fun createConversation(title: String = "New Agent Mission"): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insert(
            ConversationEntity(title = title.take(60), createdAt = now, updatedAt = now)
        )
    }

    suspend fun updateTitleIfDefault(conversationId: Long, firstPrompt: String) {
        val conv = conversationDao.getById(conversationId) ?: return
        if (conv.title == "New Agent Mission" || conv.title.isBlank()) {
            val clean = firstPrompt.trim().replace("\n", " ").take(42).ifEmpty { "Agent Mission" }
            conversationDao.updateTitle(conversationId, clean)
        } else {
            conversationDao.touch(conversationId)
        }
    }

    suspend fun renameConversation(conversationId: Long, newTitle: String) {
        val clean = newTitle.trim().replace("\n", " ").take(60)
        if (clean.isNotEmpty()) {
            conversationDao.updateTitle(conversationId, clean)
        }
    }

    suspend fun setPinned(conversationId: Long, pinned: Boolean) {
        conversationDao.setPinned(conversationId, pinned)
    }

    suspend fun clearMessages(conversationId: Long) {
        messageDao.clearConversation(conversationId)
        conversationDao.touch(conversationId)
    }

    suspend fun getMessagesSnapshot(conversationId: Long): List<MessageEntity> =
        messageDao.getMessagesOnce(conversationId)

    suspend fun appendMessage(message: MessageEntity): Long {
        conversationDao.touch(message.conversationId)
        return messageDao.insert(message)
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.deleteById(id)
    }

    suspend fun clearAllHistory() {
        conversationDao.deleteAll()
        db.executionLogDao().deleteAll()
    }
}
