package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("DeepSeek Agent", appName)
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
}
