package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Agent Studio", appName)
  }

  @Test
  fun `permission manager blocks insecure and loopback urls`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = com.example.permissions.PermissionManager(context)
    val settings = com.example.settings.AgentSettings(restrictNetworkDomains = true, allowedDomainsCsv = "api.example.com")
    assert(pm.isUrlPermitted("http://api.example.com", settings).isFailure)
    assert(pm.isUrlPermitted("https://127.0.0.1/admin", settings).isFailure)
    assert(pm.isUrlPermitted("https://evil.other.com", settings).isFailure)
    assert(pm.isUrlPermitted("https://api.example.com/v1/chat/completions", settings).isSuccess)
  }

  @Test
  fun `runShellTool executes commands captures output and enforces Agent Studio sandbox`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fileManager = com.example.tools.WorkspaceFileManager(context)
    val shellTool = com.example.tools.RunShellTool(context, fileManager)
    val settings = com.example.settings.AgentSettings()

    val okRes = shellTool.execute(
      buildJsonObject { put("command", "echo hello_agent_shell && pwd") },
      settings
    )
    assertTrue(okRes.success)
    assertTrue(okRes.output.contains("Exit Code: 0"))
    assertTrue(okRes.output.contains("hello_agent_shell"))
    assertTrue(okRes.output.contains("Agent Studio"))

    val failRes = shellTool.execute(
      buildJsonObject { put("command", "ls definitely_missing_file_xyz") },
      settings
    )
    assertTrue(failRes.success)
    assertTrue(failRes.output.contains("Exit Code:"))
    assertTrue(failRes.output.contains("STDERR"))

    // Blocking external device directories (/system/bin, /sdcard, ../)
    val blockedSystemBin = shellTool.execute(
      buildJsonObject { put("command", "ls /system/bin") },
      settings
    )
    assertFalse(blockedSystemBin.success)
    assertTrue((blockedSystemBin.error ?: "").contains("blocked", ignoreCase = true))

    val blockedSdcard = shellTool.execute(
      buildJsonObject { put("command", "ls /sdcard") },
      settings
    )
    assertFalse(blockedSdcard.success)
    assertTrue((blockedSdcard.error ?: "").contains("blocked", ignoreCase = true))

    val rootFile = java.io.File(fileManager.rootDir, "shell_created.txt")
    rootFile.writeText("hello from shell")
    val listed = fileManager.listAllFiles()
    assertTrue(listed.any { it.name == "shell_created.txt" && it.category == "workspace" })

    val blockedPrefs = shellTool.execute(
      buildJsonObject { put("command", "cat ../shared_prefs/keystore_vault.xml") },
      settings
    )
    assertFalse(blockedPrefs.success)
    assertTrue((blockedPrefs.error ?: "").contains("blocked", ignoreCase = true))

    val blockedDcl = shellTool.execute(
      buildJsonObject { put("command", "dalvikvm -cp payload.dex Main") },
      settings
    )
    assertFalse(blockedDcl.success)
    assertTrue((blockedDcl.error ?: "").contains("blocked", ignoreCase = true))
  }

  @Test
  fun `accessibility bridge provider handles ping and disconnected calls safely`() {
    val provider = com.example.accessibility.AccessibilityBridgeProvider()
    assertTrue(provider.onCreate())

    val pingBundle = provider.call(com.example.accessibility.AgentAccessibilityService.METHOD_PING, null, null)
    assertFalse(pingBundle.getBoolean(com.example.accessibility.AgentAccessibilityService.KEY_CONNECTED, true))

    val actionBundle = provider.call(
      com.example.accessibility.AgentAccessibilityService.METHOD_ACTION,
      "read_screen",
      null
    )
    assertFalse(actionBundle.getBoolean(com.example.accessibility.AgentAccessibilityService.KEY_SUCCESS, true))
    assertTrue(
      (actionBundle.getString(com.example.accessibility.AgentAccessibilityService.KEY_ERROR) ?: "")
        .contains("not connected", ignoreCase = true)
    )
  }

  @Test
  fun `provider registry supports saving, editing after save, and model id management`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vault = object : com.example.authentication.SecretVault {
      private val map = mutableMapOf<String, String>()
      override fun putSecret(key: String, plainText: String) {
        if (plainText.isBlank()) map.remove(key) else map[key] = plainText
      }
      override fun getSecret(key: String): String? = map[key]
      override fun hasSecret(key: String): Boolean = !map[key].isNullOrBlank()
      override fun removeSecret(key: String) { map.remove(key) }
      override fun clearAll() { map.clear() }
    }
    val store = com.example.settings.ProviderRegistryStore(context, vault)
    store.clearAll()

    val saved = store.saveOrUpdateProvider(
      existingId = null,
      name = "Fasfas AI",
      baseUrl = "https://api.fasfas.ai/v1",
      apiKey = "sk-test-secret",
      selectedModelId = "glm-5.3-flash",
      modelIds = listOf("glm-5.3-flash", "mai-image-2.6-flash"),
      verifiedNow = true,
      setActive = true
    )
    assertEquals("Fasfas AI", saved.name)
    assertEquals("https://api.fasfas.ai/v1", saved.baseUrl)
    assertTrue(store.hasProviderKey(saved.id))

    // Edit after save without replacing API key
    val edited = store.saveOrUpdateProvider(
      existingId = saved.id,
      name = "Fasfas RP",
      baseUrl = "https://api.fasfas.ai/rp/v1",
      apiKey = "",
      selectedModelId = "glm-5.3-flash",
      modelIds = listOf("glm-5.3-flash", "custom-model-v2"),
      verifiedNow = false,
      setActive = true
    )
    assertEquals(saved.id, edited.id)
    assertEquals("Fasfas RP", edited.name)
    assertEquals("https://api.fasfas.ai/rp/v1", edited.baseUrl)
    assertTrue(edited.modelIds.contains("custom-model-v2"))
    assertEquals("sk-test-secret", store.getProviderKey(edited.id))

    val added = store.addCustomModelId(edited.id, "gpt-4.1", selectImmediately = true)
    assertEquals("gpt-4.1", added?.selectedModelId)

    val removed = store.removeModelId(edited.id, "custom-model-v2")
    assertFalse(removed?.modelIds?.contains("custom-model-v2") ?: true)
  }

  @Test
  fun `agent profile store supports catalog install, markdown import, and edit after save`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.example.agent.AgentProfileStore(context)
    store.resetToDefaults()

    val installed = store.installCatalogAgent("research", activate = true)
    assertEquals("@research", installed?.handle)
    assertTrue(installed?.isInstalled == true)
    assertEquals("research", store.activeAgentId.value)

    val importedRes = store.installFromSpecText(
      """
      ---
      name: Bug Hunter
      handle: @hunter
      model: glm-5.3-flash
      tools: [run_shell, read_document]
      ---
      Track down bugs cleanly.
      """.trimIndent()
    )
    assertTrue(importedRes.isSuccess)
    val imported = importedRes.getOrThrow()
    assertEquals("@hunter", imported.handle)
    assertEquals("glm-5.3-flash", imported.preferredModelId)
    assertEquals(listOf("run_shell", "read_document"), imported.allowedTools)

    // Edit after save
    val updated = store.saveOrUpdateAgent(
      existingId = imported.id,
      name = "Bug Hunter Pro",
      handle = "hunter-pro",
      description = "Updated description",
      systemPrompt = "Updated instructions",
      preferredModelId = "o4-mini",
      allowedTools = listOf("run_shell"),
      disableAllTools = false,
      activate = true
    )
    assertEquals(imported.id, updated.id)
    assertEquals("@hunter-pro", updated.handle)
    assertEquals("o4-mini", updated.preferredModelId)
    assertEquals(listOf("run_shell"), updated.allowedTools)
  }

  @Test
  fun `workspace file manager resolves created files by bare name and Agent Studio prefix`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fileManager = com.example.tools.WorkspaceFileManager(context)
    val createTool = com.example.tools.CreateFileTool(fileManager)
    val readTool = com.example.tools.ReadDocumentTool(fileManager)
    val settings = com.example.settings.AgentSettings()

    val created = createTool.execute(
      buildJsonObject {
        put("filename", "Agent Studio/reports/summary.md")
        put("content", "# Hello Agent Studio\nEverything works.")
      },
      settings
    )
    assertTrue(created.success)

    // Bare filename lookup (fixes "No file Found in the Directory")
    val readBare = readTool.execute(
      buildJsonObject { put("uri_or_path", "summary.md") },
      settings
    )
    assertTrue(readBare.success)
    assertTrue(readBare.output.contains("Everything works."))

    // Subfolder prefix lookup
    val readPrefixed = readTool.execute(
      buildJsonObject { put("uri_or_path", "Agent Studio/reports/summary.md") },
      settings
    )
    assertTrue(readPrefixed.success)
  }

  @Test
  fun `zipArchiveTool and shell zip shim compress list and extract safely`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fileManager = com.example.tools.WorkspaceFileManager(context)
    fileManager.writeTextFile("notes", "alpha.txt", "Alpha Payload")
    val shellTool = com.example.tools.RunShellTool(context, fileManager)
    val settings = com.example.settings.AgentSettings()

    val zipRes = shellTool.execute(
      buildJsonObject { put("command", "zip bundle.zip notes/alpha.txt") },
      settings
    )
    assertTrue(zipRes.success)
    assertTrue(zipRes.output.contains("bundle.zip"))

    val listRes = shellTool.execute(
      buildJsonObject { put("command", "unzip -l bundle.zip") },
      settings
    )
    assertTrue(listRes.success)
    assertTrue(listRes.output.contains("alpha.txt"))

    val unzipRes = shellTool.execute(
      buildJsonObject { put("command", "unzip bundle.zip -d notes/unpacked") },
      settings
    )
    assertTrue(unzipRes.success)
    val extractedFile = fileManager.resolveExistingWorkspaceFile("notes/unpacked/alpha.txt")
    assertTrue(extractedFile.exists())
    assertEquals("Alpha Payload", extractedFile.readText())
  }

  @Test
  fun `tokenUsageStore aggregates overall per provider and per model buckets`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = com.example.settings.TokenUsageStore(context)
    store.reset()

    store.recordTurn(
      com.example.settings.TurnTokenUsage(
        providerId = "p1",
        providerName = "Fasfas AI",
        modelId = "glm-5.3-flash",
        inputInstructionsTokens = 100,
        inputMessagesTokens = 40,
        outputThinkingTokens = 25,
        outputResponseTokens = 35,
        isApiReported = true
      )
    )
    store.recordTurn(
      com.example.settings.TurnTokenUsage(
        providerId = "p2",
        providerName = "OpenRouter",
        modelId = "gpt-4o-mini",
        inputInstructionsTokens = 80,
        inputMessagesTokens = 20,
        outputThinkingTokens = 0,
        outputResponseTokens = 50,
        isApiReported = false
      )
    )

    val snap = store.snapshot.value
    assertEquals(2L, snap.overall.requestCount)
    assertEquals(180L, snap.overall.inputInstructionsTokens)
    assertEquals(60L, snap.overall.inputMessagesTokens)
    assertEquals(25L, snap.overall.outputThinkingTokens)
    assertEquals(85L, snap.overall.outputResponseTokens)
    assertEquals(350L, snap.overall.totalTokens)
    assertEquals(2, snap.byProvider.size)
    assertEquals(2, snap.byModel.size)
  }

  @Test
  fun `swarmOrchestrator coordinates drafts critiques and equalizes early`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vault = object : com.example.authentication.SecretVault {
      private val m = mutableMapOf<String, String>()
      override fun putSecret(key: String, plainText: String) { m[key] = plainText }
      override fun getSecret(key: String): String? = m[key]
      override fun hasSecret(key: String): Boolean = m[key]?.isNotBlank() == true
      override fun removeSecret(key: String) { m.remove(key) }
      override fun clearAll() { m.clear() }
    }
    val db = com.example.database.AppDatabase.getInstance(context)
    val auth = com.example.authentication.AuthManager(context, vault)
    val reg = com.example.settings.ProviderRegistryStore(context, vault)
    reg.clearAll()
    val p1 = reg.saveOrUpdateProvider(null, "Provider A", "https://api.fasfas.ai/v1", "sk-1", "glm-5.3-flash", listOf("glm-5.3-flash"))
    val p2 = reg.saveOrUpdateProvider(null, "Provider B", "https://openrouter.ai/api/v1", "sk-2", "gpt-4o-mini", listOf("gpt-4o-mini"))
    assertEquals(2, reg.providers.value.size)

    val agents = com.example.agent.AgentProfileStore(context)
    agents.resetToDefaults()
    val convMgr = com.example.agent.ConversationManager(db)
    val convId = convMgr.createConversation("Swarm Test")
    val planner = com.example.execution.TaskPlanner(db)
    val (planId, _) = planner.createPlanForRequest(convId, "Design architecture")
    val tokenStore = com.example.settings.TokenUsageStore(context)
    tokenStore.reset()

    var callsCount = 0
    val fakeOrchestrator = com.example.agent.SwarmOrchestrator(
      authManager = auth,
      providerRegistryStore = reg,
      agentProfileStore = agents,
      conversationManager = convMgr,
      taskPlanner = planner,
      toolRegistry = com.example.tools.ToolRegistry(emptyList()),
      toolExecutor = com.example.tools.ToolExecutor(com.example.tools.ToolRegistry(emptyList())),
      permissionManager = com.example.permissions.PermissionManager(context),
      memoryManager = com.example.memory.MemoryManager(db),
      executionMonitor = com.example.execution.ExecutionMonitor(),
      errorRecoveryManager = com.example.execution.ErrorRecoveryManager(),
      tokenUsageStore = tokenStore,
      modelInvoker = { req ->
        callsCount++
        val text = when {
          req.systemPrompt.contains("Swarm Equalizer") -> "Unified Equalized Final Plan"
          req.systemPrompt.contains("Cross-check") -> "Peer drafts verified.\nCONVERGED: YES"
          else -> "Proposal from ${req.providerName}"
        }
        com.example.model.ModelResponse.TextResponse(
          content = text,
          tokenUsage = com.example.settings.TurnTokenUsage(
            providerId = req.providerId ?: "p1",
            providerName = req.providerName ?: "Provider",
            modelId = req.settings.modelId,
            inputInstructionsTokens = 40,
            inputMessagesTokens = 20,
            outputThinkingTokens = 10,
            outputResponseTokens = 25,
            isApiReported = true
          )
        )
      }
    )

    val cfg = com.example.agent.SwarmConfig(
      enabled = true,
      maxCritiqueRounds = 3,
      members = listOf(
        com.example.agent.SwarmMemberSlot("s1", "build", p1.id, "glm-5.3-flash", "Planner"),
        com.example.agent.SwarmMemberSlot("s2", "code", p2.id, "gpt-4o-mini", "Auditor")
      )
    )
    val ok = fakeOrchestrator.runSwarmMission(
      conversationId = convId,
      planId = planId,
      userPrompt = "Compare microservices vs modular monolith",
      cleanHistory = emptyList(),
      baseSettings = com.example.settings.AgentSettings(modelId = "glm-5.3-flash"),
      swarmConfig = cfg
    )
    assertTrue(ok)
    // 2 drafts + 2 round-1 critiques (stopped early!) + 1 equalizer = 5 calls (not 2 + 6 + 1 = 9)
    assertEquals(5, callsCount)
    val msgs = convMgr.getMessagesSnapshot(convId)
    assertTrue(msgs.any { it.content.contains("Unified Equalized Final Plan") })
    assertEquals(5L, tokenStore.snapshot.value.overall.requestCount)
    assertEquals(2, tokenStore.snapshot.value.byProvider.size)
  }
}
