package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in AccessibilityService for Agent Studio UI automation & screen reading.
 * Runs in an isolated ':a11y' process so heavy tasks, WebViews, or shell commands in the
 * main app process cannot stall or destabilize the Accessibility binder.
 * Never reads password-masked nodes and performs work only on explicit user/agent request.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no-op: passive scraping is disabled.
        // Screen reads happen strictly on-demand via AccessibilityBridgeProvider.
    }

    override fun onInterrupt() {
        // Required override; no active speech/feedback streams to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun readVisibleScreenSummary(maxItems: Int = 40): String {
        val root = runCatching { rootInActiveWindow }.getOrNull()
            ?: return "No active window accessible."
        val lines = mutableListOf<String>()
        val visited = intArrayOf(0)
        collectReadableText(root, lines, maxItems, depth = 0, visited = visited)
        return if (lines.isEmpty()) {
            "No readable non-sensitive text found on the current screen."
        } else {
            lines.joinToString("\n")
        }
    }

    private fun collectReadableText(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        maxItems: Int,
        depth: Int,
        visited: IntArray
    ) {
        if (node == null || out.size >= maxItems || depth > MAX_TREE_DEPTH || visited[0] >= MAX_VISITED_NODES) {
            return
        }
        visited[0]++

        val isPassword = runCatching { node.isPassword }.getOrDefault(true)
        if (isPassword) return // Strict security: never read password fields

        val isVisible = runCatching { node.isVisibleToUser }.getOrDefault(true)
        if (isVisible) {
            val text = runCatching { node.text?.toString()?.trim() }.getOrNull()
            val desc = runCatching { node.contentDescription?.toString()?.trim() }.getOrNull()
            val label = text?.takeIf { it.isNotEmpty() } ?: desc?.takeIf { it.isNotEmpty() }
            if (label != null) {
                val isClickable = runCatching { node.isClickable }.getOrDefault(false)
                val clickableTag = if (isClickable) " [clickable]" else ""
                out.add("- $label$clickableTag")
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0).coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (i in 0 until childCount) {
            if (out.size >= maxItems || visited[0] >= MAX_VISITED_NODES) break
            val child = runCatching { node.getChild(i) }.getOrNull()
            if (child != null) {
                collectReadableText(child, out, maxItems, depth + 1, visited)
            }
        }
    }

    fun clickNodeByText(targetText: String): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val matches = runCatching { root.findAccessibilityNodeInfosByText(targetText) }.getOrNull()
            ?: return false
        for (node in matches) {
            if (runCatching { node.isPassword }.getOrDefault(true)) continue
            var current: AccessibilityNodeInfo? = node
            var hops = 0
            while (current != null && hops < MAX_PARENT_HOPS) {
                if (runCatching { current.isClickable }.getOrDefault(false)) {
                    val clicked = runCatching {
                        current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }.getOrDefault(false)
                    if (clicked) return true
                }
                current = runCatching { current.parent }.getOrNull()
                hops++
            }
        }
        return false
    }

    fun scrollForward(): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val visited = intArrayOf(0)
        return findAndScroll(root, depth = 0, visited = visited)
    }

    private fun findAndScroll(
        node: AccessibilityNodeInfo?,
        depth: Int,
        visited: IntArray
    ): Boolean {
        if (node == null || depth > MAX_TREE_DEPTH || visited[0] >= MAX_VISITED_NODES) return false
        visited[0]++

        if (runCatching { node.isScrollable }.getOrDefault(false)) {
            val scrolled = runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }.getOrDefault(false)
            if (scrolled) return true
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0).coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (i in 0 until childCount) {
            if (visited[0] >= MAX_VISITED_NODES) break
            val child = runCatching { node.getChild(i) }.getOrNull()
            if (child != null && findAndScroll(child, depth + 1, visited)) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val MAX_TREE_DEPTH = 24
        private const val MAX_VISITED_NODES = 250
        private const val MAX_CHILDREN_PER_NODE = 64
        private const val MAX_PARENT_HOPS = 12
        private const val PARENT_DISPATCH_TIMEOUT_MS = 4000L

        const val METHOD_PING = "ping"
        const val METHOD_ACTION = "action"
        const val KEY_ACTION = "action"
        const val KEY_TARGET_TEXT = "target_text"
        const val KEY_CONNECTED = "connected"
        const val KEY_SUCCESS = "success"
        const val KEY_OUTPUT = "output"
        const val KEY_ERROR = "error"

        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        private fun bridgeUri(context: Context): Uri =
            Uri.parse("content://${context.packageName}.a11ybridge")

        fun isBridgeConnected(context: Context): Boolean {
            if (instance != null) return true
            return runCatching {
                val res = context.contentResolver.call(
                    bridgeUri(context),
                    METHOD_PING,
                    null,
                    null
                )
                res?.getBoolean(KEY_CONNECTED, false) == true
            }.getOrDefault(false)
        }

        fun executeRemoteAction(
            context: Context,
            action: String,
            targetText: String?
        ): Triple<Boolean, String, String?> {
            val inProcess = instance
            if (inProcess != null) {
                return runOnServiceMainThread(inProcess, action, targetText)
            }
            return runCatching {
                val args = Bundle().apply {
                    putString(KEY_ACTION, action)
                    if (!targetText.isNullOrBlank()) putString(KEY_TARGET_TEXT, targetText)
                }
                val res = context.contentResolver.call(
                    bridgeUri(context),
                    METHOD_ACTION,
                    action,
                    args
                ) ?: return Triple(false, "", "Accessibility bridge did not respond.")
                val ok = res.getBoolean(KEY_SUCCESS, false)
                val out = res.getString(KEY_OUTPUT).orEmpty()
                val err = res.getString(KEY_ERROR)
                Triple(ok, out, err)
            }.getOrElse { e ->
                Triple(false, "", "Accessibility IPC failed: ${e.message ?: "Service unavailable"}")
            }
        }

        internal fun runOnServiceMainThread(
            service: AgentAccessibilityService,
            action: String,
            targetText: String?
        ): Triple<Boolean, String, String?> {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return performActionInternal(service, action, targetText)
            }
            val resultRef = AtomicReference<Triple<Boolean, String, String?>>()
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                try {
                    resultRef.set(performActionInternal(service, action, targetText))
                } catch (t: Throwable) {
                    resultRef.set(Triple(false, "", "Accessibility error: ${t.message ?: "Unknown"}"))
                } finally {
                    latch.countDown()
                }
            }
            val completed = runCatching {
                latch.await(PARENT_DISPATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
            if (!completed) {
                return Triple(false, "", "Accessibility service timed out responding to '$action'.")
            }
            return resultRef.get() ?: Triple(false, "", "Accessibility action returned no result.")
        }

        private fun performActionInternal(
            service: AgentAccessibilityService,
            action: String,
            targetText: String?
        ): Triple<Boolean, String, String?> {
            return when (action) {
                "read_screen" -> {
                    val summary = service.readVisibleScreenSummary()
                    Triple(true, "Visible Screen Content:\n$summary", null)
                }
                "click_text" -> {
                    val target = targetText?.trim()
                    if (target.isNullOrEmpty()) {
                        Triple(false, "", "Missing target_text for click_text.")
                    } else {
                        val clicked = service.clickNodeByText(target)
                        if (clicked) Triple(true, "Triggered Accessibility click on '$target'.", null)
                        else Triple(false, "", "No clickable element matching '$target' found.")
                    }
                }
                "scroll_forward" -> {
                    val scrolled = service.scrollForward()
                    if (scrolled) Triple(true, "Scrolled forward.", null)
                    else Triple(false, "", "No scrollable container found on screen.")
                }
                "back" -> {
                    val ok = runCatching {
                        service.performGlobalAction(GLOBAL_ACTION_BACK)
                    }.getOrDefault(false)
                    if (ok) Triple(true, "Performed global Back.", null)
                    else Triple(false, "", "Failed global Back.")
                }
                "home" -> {
                    val ok = runCatching {
                        service.performGlobalAction(GLOBAL_ACTION_HOME)
                    }.getOrDefault(false)
                    if (ok) Triple(true, "Performed global Home.", null)
                    else Triple(false, "", "Failed global Home.")
                }
                else -> Triple(false, "", "Unknown accessibility action '$action'.")
            }
        }
    }
}
