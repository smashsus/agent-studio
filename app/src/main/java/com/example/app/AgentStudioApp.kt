package com.example.app

import android.app.Application
import android.content.Context
import com.example.agent.AgentController
import com.example.agent.AgentProfileStore
import com.example.agent.ConversationManager
import com.example.authentication.AuthManager
import com.example.authentication.KeystoreVault
import com.example.database.AppDatabase
import com.example.execution.ErrorRecoveryManager
import com.example.execution.ExecutionMonitor
import com.example.execution.TaskPlanner
import com.example.memory.MemoryManager
import com.example.model.OpenAiCompatibleModelProvider
import com.example.model.WebBridgeProvider
import com.example.permissions.PermissionManager
import com.example.settings.ProviderRegistryStore
import com.example.settings.SettingsStore
import com.example.tools.AccessibilityTool
import com.example.tools.AnalyzePdfTool
import com.example.tools.ClipboardTool
import com.example.tools.CreateFileTool
import com.example.tools.FetchWebpageTool
import com.example.tools.GenerateHtmlAppTool
import com.example.tools.GenerateReportTool
import com.example.tools.ListWorkspaceFilesTool
import com.example.tools.ManageTaskListTool
import com.example.tools.NotificationTool
import com.example.tools.OpenAppTool
import com.example.tools.OpenUrlTool
import com.example.tools.ReadDocumentTool
import com.example.tools.RunJavaScriptTool
import com.example.tools.RunShellTool
import com.example.tools.SaveMemoryTool
import com.example.tools.ShareContentTool
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import com.example.tools.WebSearchTool
import com.example.tools.WorkspaceFileManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database = AppDatabase.getInstance(appContext)
    val settingsStore = SettingsStore(appContext)
    val keystoreVault = KeystoreVault(appContext)
    val authManager = AuthManager(appContext, keystoreVault)
    val providerRegistryStore = ProviderRegistryStore(appContext, keystoreVault)
    val agentProfileStore = AgentProfileStore(appContext)
    val permissionManager = PermissionManager(appContext)
    val memoryManager = MemoryManager(database)
    val conversationManager = ConversationManager(database)
    val fileManager = WorkspaceFileManager(appContext)
    val taskPlanner = TaskPlanner(database)
    val executionMonitor = ExecutionMonitor()
    val errorRecoveryManager = ErrorRecoveryManager()

    val toolRegistry = ToolRegistry(
        listOf(
            WebSearchTool(permissionManager),
            FetchWebpageTool(permissionManager),
            GenerateReportTool(fileManager),
            ReadDocumentTool(fileManager),
            AnalyzePdfTool(fileManager),
            CreateFileTool(fileManager),
            ListWorkspaceFilesTool(fileManager),
            RunJavaScriptTool(appContext),
            RunShellTool(appContext, fileManager),
            GenerateHtmlAppTool(fileManager),
            OpenUrlTool(appContext, permissionManager),
            OpenAppTool(appContext),
            ShareContentTool(appContext, fileManager),
            ClipboardTool(appContext),
            NotificationTool(appContext, permissionManager),
            AccessibilityTool(appContext),
            ManageTaskListTool(database),
            SaveMemoryTool(memoryManager)
        )
    )

    val toolExecutor = ToolExecutor(toolRegistry)
    private val webBridgeProvider = WebBridgeProvider()
    private val apiProvider = OpenAiCompatibleModelProvider(authManager)

    val agentController = AgentController(
        db = database,
        authManager = authManager,
        conversationManager = conversationManager,
        taskPlanner = taskPlanner,
        toolRegistry = toolRegistry,
        toolExecutor = toolExecutor,
        memoryManager = memoryManager,
        permissionManager = permissionManager,
        executionMonitor = executionMonitor,
        errorRecoveryManager = errorRecoveryManager,
        settingsStore = settingsStore,
        webBridgeProvider = webBridgeProvider,
        apiProvider = apiProvider,
        agentProfileStore = agentProfileStore
    )
}

class AgentStudioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (isMainAppProcess()) {
            container = AppContainer(this)
        }
    }

    private fun isMainAppProcess(): Boolean {
        val procName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching {
                java.io.File("/proc/self/cmdline").readText().trim('\u0000').trim()
            }.getOrNull()
        }
        return procName.isNullOrBlank() || procName == packageName
    }
}
