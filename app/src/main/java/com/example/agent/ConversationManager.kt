package com.example.agent

import com.example.database.AppDatabase
import com.example.database.ConversationEntity
import com.example.database.MessageEntity
import kotlinx.coroutines.flow.Flow

class ConversationManager(private val db: AppDatabase) {
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    val conversations: Flow<List<ConversationEntity>> = conversationDao.observeConversations()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeMessages(conversationId)

    suspend fun ensureDefaultConversation(): Long {
        // Always returns a valid conversation id
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
