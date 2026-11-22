package com.example.accessibility

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Lightweight private ContentProvider running inside the ':a11y' process alongside
 * AgentAccessibilityService so the main app process can invoke on-demand accessibility
 * actions across process boundaries without exposing any external endpoints.
 */
class AccessibilityBridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val service = AgentAccessibilityService.instance
        return when (method) {
            AgentAccessibilityService.METHOD_PING -> Bundle().apply {
                putBoolean(AgentAccessibilityService.KEY_CONNECTED, service != null)
            }
            AgentAccessibilityService.METHOD_ACTION -> {
                if (service == null) {
                    return Bundle().apply {
                        putBoolean(AgentAccessibilityService.KEY_SUCCESS, false)
                        putString(
                            AgentAccessibilityService.KEY_ERROR,
                            "Accessibility service is not connected. Enable Agent Studio in Android Accessibility Settings."
                        )
                    }
                }
                val action = extras?.getString(AgentAccessibilityService.KEY_ACTION) ?: arg.orEmpty()
                val targetText = extras?.getString(AgentAccessibilityService.KEY_TARGET_TEXT)
                val (ok, output, error) = AgentAccessibilityService.runOnServiceMainThread(
                    service = service,
                    action = action,
                    targetText = targetText
                )
                Bundle().apply {
                    putBoolean(AgentAccessibilityService.KEY_SUCCESS, ok)
                    putString(AgentAccessibilityService.KEY_OUTPUT, output)
                    if (error != null) putString(AgentAccessibilityService.KEY_ERROR, error)
                }
            }
            else -> Bundle().apply {
                putBoolean(AgentAccessibilityService.KEY_SUCCESS, false)
                putString(AgentAccessibilityService.KEY_ERROR, "Unsupported method '$method'.")
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
