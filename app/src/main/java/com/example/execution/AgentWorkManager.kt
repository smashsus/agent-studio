package com.example.execution

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.app.DeepSeekAgentApp
import com.example.permissions.PermissionManager
import com.example.tools.NotificationTool

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "DeepSeek Agent Reminder"
        val message = inputData.getString(KEY_MESSAGE) ?: "Scheduled agent task reminder."

        val permissionManager = PermissionManager(applicationContext)
        if (!permissionManager.hasNotificationPermission()) {
            return Result.success()
        }

        NotificationTool.ensureNotificationChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, NotificationTool.AGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify((System.currentTimeMillis() % 100_000).toInt(), notification)
        }
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
    }
}

class BackgroundAgentWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val conversationId = inputData.getLong(KEY_CONVERSATION_ID, -1L)
        val prompt = inputData.getString(KEY_PROMPT) ?: return Result.failure()
        if (conversationId <= 0L) return Result.failure()

        val container = (applicationContext as DeepSeekAgentApp).container
        container.agentController.runMissionInCurrentScope(
            conversationId = conversationId,
            userPrompt = prompt,
            attachmentUri = null,
            attachmentName = null
        )

        if (container.permissionManager.hasNotificationPermission()) {
            NotificationTool.ensureNotificationChannel(applicationContext)
            val notification = NotificationCompat.Builder(applicationContext, NotificationTool.AGENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Background Agent Task Finished")
                .setContentText("Completed: ${prompt.take(80)}")
                .setAutoCancel(true)
                .build()
            runCatching {
                NotificationManagerCompat.from(applicationContext)
                    .notify(conversationId.toInt(), notification)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_PROMPT = "prompt"

        fun enqueue(context: Context, conversationId: Long, prompt: String) {
            val data = Data.Builder()
                .putLong(KEY_CONVERSATION_ID, conversationId)
                .putString(KEY_PROMPT, prompt)
                .build()
            val request = OneTimeWorkRequestBuilder<BackgroundAgentWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
