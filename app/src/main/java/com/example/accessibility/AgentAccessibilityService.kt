package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Opt-in AccessibilityService for DeepSeek Agent UI automation & screen reading.
 * Disabled by default; performs actions ONLY when explicitly invoked by a user-authorized tool
 * after explicit in-app consent and system Settings activation.
 * Never reads password-masked nodes.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive: no background scraping or logging. Reads happen only on explicit user/agent request.
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun readVisibleScreenSummary(maxItems: Int = 40): String {
        val root = rootInActiveWindow ?: return "No active window accessible."
        val lines = mutableListOf<String>()
        collectReadableText(root, lines, maxItems)
        return if (lines.isEmpty()) {
            "No readable non-sensitive text found on the current screen."
        } else {
            lines.joinToString("\n")
        }
    }

    private fun collectReadableText(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        maxItems: Int
    ) {
        if (node == null || out.size >= maxItems) return
        if (node.isPassword) return // Strict security: never read password fields

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val label = text?.takeIf { it.isNotEmpty() } ?: desc?.takeIf { it.isNotEmpty() }
        if (label != null) {
            val clickableTag = if (node.isClickable) " [clickable]" else ""
            out.add("- $label$clickableTag")
        }
        for (i in 0 until node.childCount) {
            if (out.size >= maxItems) break
            collectReadableText(node.getChild(i), out, maxItems)
        }
    }

    fun clickNodeByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(targetText)
        for (node in matches) {
            if (node.isPassword) continue
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            }
        }
        return false
    }

    fun scrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndScroll(root)
    }

    private fun findAndScroll(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isScrollable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        for (i in 0 until node.childCount) {
            if (findAndScroll(node.getChild(i))) return true
        }
        return false
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }
}
