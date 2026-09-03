package com.example.agyremote.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import com.example.agyremote.ui.webview.scripts.AgyActionScript
import com.example.agyremote.ui.webview.scripts.AgySessionScript
import com.example.agyremote.ui.webview.scripts.AgyThemeScript

private var lastNavTimestamp = 0L

fun executeAgyNav(webView: WebView?, action: String) {
    val now = System.currentTimeMillis()
    if (now - lastNavTimestamp < 350L) {
        return
    }
    lastNavTimestamp = now

    val js = """
        (function() {
            function getRouter() {
                return window.__TSR_ROUTER__;
            }

            function getPath() {
                const r = getRouter();
                return (r && r.state && r.state.location && r.state.location.pathname) || window.location.pathname;
            }

            function getActiveConvoPath() {
                let p = window.__agyLastActiveConvo;
                if (!p) {
                    try { p = localStorage.getItem('agy_last_active_convo'); } catch(e){}
                }
                if (!p || !p.startsWith('/c/')) {
                    const first = document.querySelector('a[href^="/c/"]');
                    if (first) p = first.getAttribute('href');
                    else p = '/c/f1b40f64-6ccf-4007-834e-444183cd4157';
                }
                return p;
            }

            function navigateTo(targetPath, screenKey) {
                if (!targetPath) return;
                const auxBtn = document.querySelector('[data-testid="mobile-toggle-aux-sidebar"]') || 
                               document.querySelector('button[aria-label="Toggle Auxiliary Pane"]');
                const hasAux = document.querySelector('[data-testid="changed-file-row"]') || 
                               document.querySelector('[data-testid="aux-panel-plus-dropdown-trigger"]');
                if (hasAux && auxBtn) auxBtn.click();

                const r = getRouter();
                if (r && typeof r.navigate === 'function') {
                    if (targetPath.startsWith('/c/')) {
                        const cid = targetPath.replace('/c/', '');
                        r.navigate({ to: '/c/${'$'}cascadeId', params: { cascadeId: cid } });
                    } else {
                        r.navigate({ to: targetPath });
                    }
                } else {
                    const domLink = document.querySelector('a[href="' + targetPath + '"]');
                    if (domLink) {
                        domLink.click();
                    } else {
                        window.history.pushState(null, '', targetPath);
                        window.dispatchEvent(new PopStateEvent('popstate'));
                    }
                }

                if (window.AgyAndroidBridge && window.AgyAndroidBridge.reportNavigation) {
                    window.AgyAndroidBridge.reportNavigation(screenKey);
                }
            }

            const path = getPath();
            const action = '$action';

            if (action === 'swipe_left') {
                // Vuốt từ phải sang trái (-> tiến: Dự án -> Phiên đang mở -> Lịch sử)
                if (path === '/' || path === '') {
                    navigateTo(getActiveConvoPath(), 'active_convo');
                } else if (path.startsWith('/c/')) {
                    navigateTo('/history', 'history');
                }
            } else if (action === 'swipe_right') {
                // Vuốt từ trái sang phải (-> lùi: Lịch sử -> Phiên đang mở -> Dự án)
                if (path.startsWith('/history')) {
                    navigateTo(getActiveConvoPath(), 'active_convo');
                } else if (path.startsWith('/c/')) {
                    navigateTo('/', 'projects');
                }
            } else if (action === 'toggle_menu') {
                // Nhấn Menu: Toggle giữa Dự án/phiên và Phiên đang mở
                if (path.startsWith('/c/')) {
                    navigateTo('/', 'projects');
                } else {
                    navigateTo(getActiveConvoPath(), 'active_convo');
                }
            } else if (action === 'go_projects') {
                // Điều hướng trực tiếp về màn hình Dự án / Phiên ('/')
                navigateTo('/', 'projects');
            }
        })();
    """.trimIndent()
    webView?.post { webView.evaluateJavascript(js, null) }
}

fun handleSwipeLeft(webView: WebView?) {
    executeAgyNav(webView, "swipe_left")
}

fun handleSwipeRight(webView: WebView?) {
    executeAgyNav(webView, "swipe_right")
}

fun toggleAgySidebar(webView: WebView?) {
    executeAgyNav(webView, "toggle_menu")
}

fun navigateToProjects(webView: WebView?) {
    executeAgyNav(webView, "go_projects")
}

fun navigateToHistory(webView: WebView?) {
    handleSwipeLeft(webView)
}

class AgyJsBridge(
    private val onStatus: (Boolean) -> Unit,
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit,
    private val onNavigate: (String) -> Unit,
    private val onSessionInfo: (String, String) -> Unit = { _, _ -> }
) {
    @android.webkit.JavascriptInterface
    fun reportWorkingStatus(isWorking: Boolean) {
        onStatus(isWorking)
    }

    @android.webkit.JavascriptInterface
    fun reportSessionInfo(path: String, title: String) {
        onSessionInfo(path, title)
    }

    @android.webkit.JavascriptInterface
    fun triggerSwipeLeft() {
        onSwipeLeft()
    }

    @android.webkit.JavascriptInterface
    fun triggerSwipeRight() {
        onSwipeRight()
    }

    @android.webkit.JavascriptInterface
    fun reportNavigation(screen: String) {
        onNavigate(screen)
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun AgyWebView(
    url: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    onTitleReceived: (String) -> Unit = {},
    onWorkingStatusChanged: (Boolean) -> Unit = {},
    onNavigationChanged: (String) -> Unit = {},
    onSwipeLeftDetected: () -> Unit = {},
    onSwipeRightDetected: () -> Unit = {},
    onErrorReceived: (String) -> Unit = {},
    onLogReceived: (tag: String, message: String, isError: Boolean) -> Unit = { _, _, _ -> },
    onAuthCodeCaptured: (String) -> Unit = {},
    onRequestFileChooser: (ValueCallback<Array<Uri>>) -> Unit = {},
    onSessionInfoReceived: (path: String, title: String) -> Unit = { _, _ -> },
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val bgColor = if (isDarkTheme) android.graphics.Color.parseColor("#121316") else android.graphics.Color.WHITE
            setBackgroundColor(bgColor)
            WebView.setWebContentsDebuggingEnabled(true)

            // Cầu nối Javascript Interface
            addJavascriptInterface(
                AgyJsBridge(
                    onStatus = { isWorking -> onWorkingStatusChanged(isWorking) },
                    onSwipeLeft = {
                        post {
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            handleSwipeLeft(this@apply)
                            onSwipeLeftDetected()
                        }
                    },
                    onSwipeRight = {
                        post {
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            handleSwipeRight(this@apply)
                            onSwipeRightDetected()
                        }
                    },
                    onNavigate = { screen ->
                        post {
                            onNavigationChanged(screen)
                        }
                    },
                    onSessionInfo = { path, title ->
                        post {
                            onSessionInfoReceived(path, title)
                        }
                    }
                ),
                "AgyAndroidBridge"
            )

            // Bắt cử chỉ vuốt ở tầng Native Touch
            var touchDownX = 0f
            var touchDownY = 0f
            var touchDownTime = 0L

            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        touchDownTime = System.currentTimeMillis()
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val diffX = event.x - touchDownX
                        val diffY = event.y - touchDownY
                        val duration = System.currentTimeMillis() - touchDownTime
                        if (duration < 500 && kotlin.math.abs(diffX) > kotlin.math.abs(diffY) * 1.25f) {
                            if (diffX < -90f) {
                                // Vuốt từ phải sang trái
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                handleSwipeLeft(this@apply)
                                onSwipeLeftDetected()
                                return@setOnTouchListener true
                            } else if (diffX > 90f) {
                                // Vuốt từ trái sang phải
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                handleSwipeRight(this@apply)
                                onSwipeRightDetected()
                                return@setOnTouchListener true
                            }
                        }
                    }
                }
                false
            }

            // Bật Cookie và Third-party cookies để duy trì phiên đăng nhập Google
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                
                // Mở cửa sổ cùng WebView để đăng nhập In-App không bị văng ra ngoài
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = true

                // Giả lập chuẩn Chrome Mobile 100% để Google OAuth cho phép đăng nhập In-App không bị lỗi 403 disallowed_useragent
                userAgentString = "Mozilla/5.0 (Linux; Android 14; 23013PC75G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val currentUrl = url ?: ""
                    onPageStarted(currentUrl)
                    onLogReceived("PAGE_START", currentUrl, false)

                    // Bắt Authorization Code nếu Google redirect về callback
                    checkAndExtractAuthCode(currentUrl)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val currentUrl = url ?: ""
                    onPageFinished(currentUrl)
                    onLogReceived("PAGE_FINISH", currentUrl, false)

                    // Nạp Chrome Polyfill
                    view?.evaluateJavascript(
                        """
                        if (!window.chrome) {
                            window.chrome = { app: { isInstalled: false }, runtime: {} };
                        }
                        """.trimIndent(),
                        null
                    )

                    // Nạp từng module Script độc lập (được bọc trong try-catch riêng biệt, cách ly lỗi hoàn toàn)
                    view?.evaluateJavascript(AgyThemeScript.getScript(), null)
                    view?.evaluateJavascript(AgyActionScript.getScript(), null)
                    view?.evaluateJavascript(AgySessionScript.getScript(), null)

                    checkAndExtractAuthCode(currentUrl)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString() ?: return false
                    onLogReceived("NAVIGATE", targetUrl, false)

                    // Bắt callback Google OAuth
                    if (checkAndExtractAuthCode(targetUrl)) {
                        return true
                    }

                    // TẤT CẢ các URL (bao gồm Google OAuth, máy chủ LAN) đều load trực tiếp trong App
                    return false
                }

                private fun checkAndExtractAuthCode(targetUrl: String): Boolean {
                    if (targetUrl.contains("oauth-callback") || targetUrl.contains("code=")) {
                        try {
                            val uri = Uri.parse(targetUrl)
                            val code = uri.getQueryParameter("code")
                            if (!code.isNullOrBlank()) {
                                onLogReceived("AUTH_SUCCESS", "Đã bắt được Authorization Code: ${code.take(10)}...", false)
                                onAuthCodeCaptured(code)
                                return true
                            }
                        } catch (e: Exception) {
                            onLogReceived("AUTH_PARSE_ERR", e.message ?: "Lỗi đọc query code", true)
                        }
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    val isMain = request?.isForMainFrame == true
                    val errorLog = "[NET_ERR] Code: ${error?.errorCode} | Mô tả: ${error?.description} | URL: ${request?.url}"
                    Log.e("AgyWebView", errorLog)
                    onLogReceived("NET_ERR", errorLog, true)

                    if (isMain) {
                        onErrorReceived(error?.description?.toString() ?: "Lỗi tải trang chính")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    val status = errorResponse?.statusCode ?: 0
                    val logMsg = "[HTTP_ERR $status] ${errorResponse?.reasonPhrase} | URL: ${request?.url}"
                    Log.w("AgyWebView", logMsg)
                    onLogReceived("HTTP_ERR", logMsg, status >= 400)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    val logMsg = "[SSL_WARN] Bỏ qua lỗi SSL nội bộ: ${error?.primaryError} tại ${error?.url}"
                    Log.w("AgyWebView", logMsg)
                    onLogReceived("SSL_WARN", logMsg, false)
                    handler?.proceed() // Cho phép tự ký trong mạng LAN
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        onTitleReceived(title)
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val msg = consoleMessage?.message() ?: ""
                    val level = consoleMessage?.messageLevel() ?: ConsoleMessage.MessageLevel.LOG
                    val logMsg = "[JS_${level.name}] $msg (dòng ${consoleMessage?.lineNumber()})"
                    
                    val isErr = level == ConsoleMessage.MessageLevel.ERROR
                    if (isErr) {
                        Log.e("AgyWebView_JS", logMsg)
                    } else {
                        Log.d("AgyWebView_JS", logMsg)
                    }
                    onLogReceived("JS_LOG", logMsg, isErr)

                    return super.onConsoleMessage(consoleMessage)
                }

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
                    // Nếu trang web yêu cầu mở window mới (như popup OAuth), chuyển hướng load ngay trong WebView chính
                    val href = view?.handler?.obtainMessage()
                    view?.requestFocusNodeHref(href)
                    val url = href?.data?.getString("url")
                    if (!url.isNullOrBlank()) {
                        view.loadUrl(url)
                        return true
                    }

                    // Hoặc gắn thẳng WebView hiện tại vào transport
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = view
                    resultMsg?.sendToTarget()
                    return true
                }
            }
        }
    }

    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }

    DisposableEffect(url) {
        onWebViewCreated(webView)
        webView.loadUrl(url)
        onDispose { }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}
