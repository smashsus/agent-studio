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
  fun `runShellTool executes commands captures output and enforces guardrails`() = runBlocking {
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
    assertTrue(okRes.output.contains("AgentWorkspace"))

    val failRes = shellTool.execute(
      buildJsonObject { put("command", "ls definitely_missing_file_xyz") },
      settings
    )
    assertTrue(failRes.success)
    assertTrue(failRes.output.contains("Exit Code:"))
    assertTrue(failRes.output.contains("STDERR"))

    val selinuxNoteRes = shellTool.execute(
      buildJsonObject { put("command", "pwd; echo 'ls: /system/bin: Permission denied' 1>&2; exit 1") },
      settings
    )
    assertTrue(selinuxNoteRes.success)
    assertTrue(selinuxNoteRes.output.contains("Exit Code: 1"))
    assertTrue(selinuxNoteRes.output.contains("Android SELinux Note"))

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
}
