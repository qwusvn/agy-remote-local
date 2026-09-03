package com.example.agyremote.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AgyWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPageStarted: () -> Unit = {},
    onPageFinished: () -> Unit = {},
    onErrorReceived: (String) -> Unit = {},
    onRequestFileChooser: (ValueCallback<Array<Uri>>) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setBackgroundColor(android.graphics.Color.WHITE)
            WebView.setWebContentsDebuggingEnabled(true)

            // Cho phép Cookie và Third-party cookies để xác thực Google OAuth
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true

                // Xóa định danh WebView nhúng để Google OAuth cho phép xác thực
                val defaultUa = userAgentString
                userAgentString = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
            }

            fun openInDefaultBrowser(targetUrl: String) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)),
                            "Mở trang đăng nhập"
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onPageStarted()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageFinished()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString() ?: return false
                    // Nếu là URL nội bộ máy chủ AGY thì để WebView load
                    if (targetUrl.contains(":4400")) {
                        return false
                    }
                    // Nếu là link đăng nhập Google hoặc bên ngoài -> Mở bằng trình duyệt mặc định của máy
                    openInDefaultBrowser(targetUrl)
                    return true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onErrorReceived(error?.description?.toString() ?: "Không thể kết nối tới máy chủ AGY")
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback != null) {
                        onRequestFileChooser(filePathCallback)
                        return true
                    }
                    return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val hitTest = view?.hitTestResult
                    val extraUrl = hitTest?.extra
                    if (!extraUrl.isNullOrBlank()) {
                        openInDefaultBrowser(extraUrl)
                        return true
                    }

                    val popupWebView = WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                v: WebView?,
                                req: WebResourceRequest?
                            ): Boolean {
                                val popupUrl = req?.url?.toString()
                                if (!popupUrl.isNullOrBlank()) {
                                    openInDefaultBrowser(popupUrl)
                                }
                                return true
                            }
                        }
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = popupWebView
                    resultMsg?.sendToTarget()
                    return true
                }
            }
        }
    }

    // Xử lý nút Back của Android để quay lại trang trước trong WebView nếu có thể
    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }

    DisposableEffect(url) {
        onWebViewCreated(webView)
        webView.loadUrl(url)
        onDispose {
            // cleanup if needed
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}
