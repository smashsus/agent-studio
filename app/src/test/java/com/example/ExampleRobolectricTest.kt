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
    assertEquals("Agent Harness", appName)
  }

  @Test
  fun `permission manager blocks insecure and loopback urls`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = com.example.permissions.PermissionManager(context)
    val settings = com.example.settings.AgentSettings(restrictNetworkDomains = true, allowedDomainsCsv = "api.deepseek.com")
    assert(pm.isUrlPermitted("http://api.deepseek.com", settings).isFailure)
    assert(pm.isUrlPermitted("https://127.0.0.1/admin", settings).isFailure)
    assert(pm.isUrlPermitted("https://evil.example.com", settings).isFailure)
    assert(pm.isUrlPermitted("https://api.deepseek.com/chat/completions", settings).isSuccess)
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
    assertFalse(failRes.success)
    assertTrue((failRes.error ?: "").contains("Exit Code:"))

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
}
