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
}
