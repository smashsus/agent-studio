package com.example.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.R
import com.example.accessibility.AgentAccessibilityService
import com.example.execution.ReminderWorker
import com.example.permissions.PermissionManager
import com.example.permissions.ToolRiskLevel
import com.example.settings.AgentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

class OpenUrlTool(private val context: Context, private val permissionManager: PermissionManager) : AgentTool {
    override val definition = ToolDefinition(
        name = "open_url",
        category = "Android Integration",
        description = "Opens a permitted HTTPS URL in the user's browser via Android Intent.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("url", "string", "HTTPS URL to open.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val rawUrl = args["url"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing url.")
        val check = permissionManager.isUrlPermitted(rawUrl, settings)
        if (check.isFailure) {
            return ToolResult(false, "", error = check.exceptionOrNull()?.message ?: "URL blocked.")
        }
        return withContext(Dispatchers.Main) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult(true, "Opened URL via Android Intent: $rawUrl")
            }.getOrElse { err ->
                ToolResult(false, "", error = "No handler found to open URL: ${err.message}")
            }
        }
    }
}

class OpenAppTool(private val context: Context) : AgentTool {
    override val definition = ToolDefinition(
        name = "open_app",
        category = "Android Integration",
        description = "Launches an installed Android app by package name or app display name.",
        riskLevel = ToolRiskLevel.SYSTEM,
        parameters = listOf(
            ToolParameter("app_query", "string", "App name (e.g. Chrome, Calendar) or package ID.")
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val query = args["app_query"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: return ToolResult(false, "", error = "Missing app_query.")

        return withContext(Dispatchers.Main) {
            val pm = context.packageManager
            val directIntent = pm.getLaunchIntentForPackage(query)
            if (directIntent != null) {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                return@withContext ToolResult(true, "Launched package: $query")
            }

            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(launcherIntent, 0)
            val matched = apps.firstOrNull { info ->
                val label = info.loadLabel(pm).toString()
                label.contains(query, ignoreCase = true) ||
                    info.activityInfo.packageName.contains(query, ignoreCase = true)
            }
            if (matched != null) {
                val pkg = matched.activityInfo.packageName
                val launch = pm.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launch != null) {
                    context.startActivity(launch)
                    return@withContext ToolResult(
                        true,
                        "Launched app '${matched.loadLabel(pm)}' ($pkg)."
                    )
                }
            }
            ToolResult(false, "", error = "No matching launchable app found for '$query'.")
        }
    }
}

class ShareContentTool(
    private val context: Context,
    private val fileManager: WorkspaceFileManager
) : AgentTool {
    override val definition = ToolDefinition(
        name = "share_content",
        category = "Android Integration",
        description = "Opens the Android Share Sheet to share generated text or a workspace file.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("text", "string", "Text content or summary to share.", required = false),
            ToolParameter("file_path", "string", "Optional workspace file path to attach.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val text = args["text"]?.jsonPrimitive?.contentOrNull
        val filePath = args["file_path"]?.jsonPrimitive?.contentOrNull
        if (text.isNullOrBlank() && filePath.isNullOrBlank()) {
            return ToolResult(false, "", error = "Provide either text or file_path to share.")
        }

        return withContext(Dispatchers.Main) {
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    if (!filePath.isNullOrBlank()) {
                        val file = fileManager.resolveExistingWorkspaceFile(filePath)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        type = when (file.extension.lowercase()) {
                            "pdf" -> "application/pdf"
                            "html" -> "text/html"
                            else -> "text/plain"
                        }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
                    } else {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                }
                val chooser = Intent.createChooser(intent, "Share via DeepSeek Agent").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                ToolResult(true, "Opened Android Share Sheet.")
            }.getOrElse { err ->
                ToolResult(false, "", error = "Share Sheet failed: ${err.message}")
            }
        }
    }
}

class ClipboardTool(private val context: Context) : AgentTool {
    override val definition = ToolDefinition(
        name = "clipboard_access",
        category = "Android Integration",
        description = "Reads or writes the Android clipboard respecting Android 10+ background restrictions and sensitive flags.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("action", "string", "Either 'read' or 'write'."),
            ToolParameter("text", "string", "Text to copy when action is 'write'.", required = false),
            ToolParameter("sensitive", "boolean", "Mark clipboard content as sensitive on Android 13+.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase()?.trim()
            ?: return ToolResult(false, "", error = "Missing action ('read' or 'write').")

        return withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            when (action) {
                "write" -> {
                    val text = args["text"]?.jsonPrimitive?.contentOrNull
                        ?: return@withContext ToolResult(false, "", error = "Missing text for clipboard write.")
                    val sensitive = args["sensitive"]?.jsonPrimitive?.booleanOrNull ?: false
                    val clip = ClipData.newPlainText("DeepSeek Agent", text)
                    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    ToolResult(true, "Copied ${text.length} characters to Android clipboard.")
                }
                "read" -> {
                    if (!clipboard.hasPrimaryClip()) {
                        return@withContext ToolResult(true, "Clipboard is empty.")
                    }
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.coerceToText(context)?.toString()
                    if (text.isNullOrEmpty()) {
                        ToolResult(
                            false,
                            "",
                            error = "Clipboard unavailable (Android 10+ restricts clipboard reads when app lacks window focus)."
                        )
                    } else {
                        ToolResult(true, "Clipboard content (${text.length} chars):\n${text.take(4000)}")
                    }
                }
                else -> ToolResult(false, "", error = "Unsupported clipboard action '$action'.")
            }
        }
    }
}

class NotificationTool(
    private val context: Context,
    private val permissionManager: PermissionManager
) : AgentTool {
    override val definition = ToolDefinition(
        name = "create_notification",
        category = "Android Integration",
        description = "Posts an immediate Android notification or schedules a WorkManager task reminder.",
        riskLevel = ToolRiskLevel.SENSITIVE,
        parameters = listOf(
            ToolParameter("title", "string", "Notification title."),
            ToolParameter("message", "string", "Notification body message."),
            ToolParameter("delay_seconds", "integer", "Optional delay in seconds via WorkManager.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        if (!permissionManager.hasNotificationPermission()) {
            return ToolResult(
                false,
                "",
                error = "POST_NOTIFICATIONS permission not granted. Grant notification access in Settings or workspace banner."
            )
        }
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim() ?: "DeepSeek Agent"
        val message = args["message"]?.jsonPrimitive?.contentOrNull?.trim() ?: "Task reminder"
        val delaySec = args["delay_seconds"]?.jsonPrimitive?.intOrNull ?: 0

        if (delaySec > 0) {
            val data = Data.Builder()
                .putString(ReminderWorker.KEY_TITLE, title)
                .putString(ReminderWorker.KEY_MESSAGE, message)
                .build()
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delaySec.toLong(), TimeUnit.SECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return ToolResult(true, "Scheduled WorkManager reminder '$title' in ${delaySec}s.")
        }

        ensureNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, AGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100_000).toInt(), notification)
        }.getOrElse { err ->
            return ToolResult(false, "", error = "Failed to post notification: ${err.message}")
        }
        return ToolResult(true, "Posted notification: $title")
    }

    companion object {
        const val AGENT_CHANNEL_ID = "deepseek_agent_tasks"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    AGENT_CHANNEL_ID,
                    "Agent Tasks & Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for completed autonomous tasks and scheduled reminders."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}

class AccessibilityTool : AgentTool {
    override val definition = ToolDefinition(
        name = "accessibility_interact",
        category = "Android Integration",
        description = "Reads visible non-password screen text or triggers an assisted UI action via the opt-in AccessibilityService.",
        riskLevel = ToolRiskLevel.SYSTEM,
        parameters = listOf(
            ToolParameter("action", "string", "One of: read_screen, click_text, scroll_forward, back, home."),
            ToolParameter("target_text", "string", "Visible button/label text for click_text.", required = false)
        )
    )

    override suspend fun execute(args: JsonObject, settings: AgentSettings): ToolResult {
        val service = AgentAccessibilityService.instance
            ?: return ToolResult(
                false,
                "",
                error = "Accessibility service is not connected. Enable DeepSeek Agent in Android Accessibility Settings."
            )

        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase()?.trim()
            ?: return ToolResult(false, "", error = "Missing action argument.")

        return withContext(Dispatchers.Main) {
            when (action) {
                "read_screen" -> {
                    val summary = service.readVisibleScreenSummary()
                    ToolResult(true, "Visible Screen Content:\n$summary")
                }
                "click_text" -> {
                    val target = args["target_text"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?: return@withContext ToolResult(false, "", error = "Missing target_text for click_text.")
                    val clicked = service.clickNodeByText(target)
                    if (clicked) {
                        ToolResult(true, "Triggered Accessibility click on '$target'.")
                    } else {
                        ToolResult(false, "", error = "No clickable element matching '$target' found.")
                    }
                }
                "scroll_forward" -> {
                    val scrolled = service.scrollForward()
                    if (scrolled) ToolResult(true, "Scrolled forward.")
                    else ToolResult(false, "", error = "No scrollable container found on screen.")
                }
                "back" -> {
                    val ok = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    ToolResult(ok, if (ok) "Performed global Back." else "Failed global Back.")
                }
                "home" -> {
                    val ok = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    ToolResult(ok, if (ok) "Performed global Home." else "Failed global Home.")
                }
                else -> ToolResult(false, "", error = "Unknown accessibility action '$action'.")
            }
        }
    }
}
