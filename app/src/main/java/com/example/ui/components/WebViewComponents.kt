package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.authentication.AuthManager
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun DeepSeekOfficialWebView(
    pendingBridgePrompt: String?,
    onPageFinished: (url: String?, title: String?) -> Unit,
    onConfirmConnected: () -> Unit,
    onCopyBridgePrompt: (String) -> Unit,
    initialUrl: String = AuthManager.DEEPSEEK_WEB_URL,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = LocalSwissTokens.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var loadProgress by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier,
        color = tokens.surface1,
        shape = RoundedCornerShape(SwissGeometry.radiusStandard),
        border = BorderStroke(SwissGeometry.borderWidth, tokens.border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        modifier = Modifier
                            .size(SwissGeometry.minTouchTarget)
                            .testTag("webview_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.primaryText
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier
                            .size(SwissGeometry.minTouchTarget)
                            .testTag("webview_reload_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = tokens.primaryText
                        )
                    }
                    Text(
                        text = currentUrl.removePrefix("https://"),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!pendingBridgePrompt.isNullOrBlank()) {
                        SwissOutlinedButton(
                            text = "Copy Prompt",
                            onClick = { onCopyBridgePrompt(pendingBridgePrompt) },
                            icon = Icons.Default.ContentPaste,
                            modifier = Modifier.testTag("webview_copy_prompt_button")
                        )
                    }
                    IconButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                )
                            }
                        },
                        modifier = Modifier.size(SwissGeometry.minTouchTarget)
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = "Open in External Browser",
                            tint = tokens.secondaryText
                        )
                    }
                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(SwissGeometry.minTouchTarget)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Browser",
                                tint = tokens.primaryText
                            )
                        }
                    }
                }
            }

            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    color = tokens.primaryAccent,
                    trackColor = tokens.surface3,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                SwissDivider()
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            this.settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                // Strip Android WebView '; wv' marker so modern web apps render reliably
                                val baseUa = userAgentString ?: ""
                                userAgentString = baseUa.replace("; wv)", ")").replace("Version/4.0 ", "")
                            }
                            setOnTouchListener { view, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN,
                                    MotionEvent.ACTION_MOVE -> {
                                        view.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    MotionEvent.ACTION_UP,
                                    MotionEvent.ACTION_CANCEL -> {
                                        view.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                false
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    val popupWebView = WebView(ctx).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                v: WebView?,
                                                req: WebResourceRequest?
                                            ): Boolean {
                                                val popupUrl = req?.url?.toString()
                                                if (!popupUrl.isNullOrBlank() && popupUrl.startsWith("https://")) {
                                                    view?.loadUrl(popupUrl)
                                                }
                                                return true
                                            }
                                        }
                                    }
                                    transport.webView = popupWebView
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.startsWith("https://") || url.startsWith("http://")) {
                                        return false
                                    }
                                    return true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    currentUrl = url ?: currentUrl
                                    onPageFinished(url, view?.title)
                                }
                            }
                            loadUrl(initialUrl)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            SwissDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confirm once signed in",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.weight(1f)
                )
                SwissPrimaryButton(
                    text = "Confirm Session",
                    onClick = onConfirmConnected,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.testTag("confirm_web_session_button")
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun HtmlSandboxPreviewDialog(
    title: String,
    htmlContent: String,
    onDismiss: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(SwissGeometry.radiusDialog),
            color = tokens.surface1,
            border = BorderStroke(SwissGeometry.borderWidth, tokens.border)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HTML Sandbox Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.primaryAccent
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(SwissGeometry.minTouchTarget)
                            .testTag("close_html_preview_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Preview", tint = tokens.primaryText)
                    }
                }
                SwissDivider()
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            this.settings.apply {
                                javaScriptEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                allowFileAccess = false
                                allowContentAccess = false
                                blockNetworkLoads = true
                                domStorageEnabled = false
                            }
                            setOnTouchListener { v, event ->
                                if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                false
                            }
                            webChromeClient = WebChromeClient()
                            loadDataWithBaseURL("https://sandbox.local/", htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
