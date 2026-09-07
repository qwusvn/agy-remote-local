package com.example.agyremote.ui

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agyremote.data.ConnectionConfig
import com.example.agyremote.data.ConnectionPreferences
import com.example.agyremote.media.ImageOptimizer
import com.example.agyremote.service.AgyNotificationService
import com.example.agyremote.ui.components.AccessoryCodingBar
import com.example.agyremote.ui.components.LogsBottomSheet
import com.example.agyremote.ui.components.TabBar
import com.example.agyremote.ui.components.TopStatusBar
import com.example.agyremote.ui.viewmodel.MainViewModel
import com.example.agyremote.ui.webview.scripts.AgyActionScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class BrowserTab(
    val id: String,
    val title: String,
    val url: String,
    val isWorking: Boolean = false,
    val hasUnread: Boolean = false
)

data class LogItem(
    val time: String,
    val tag: String,
    val message: String,
    val isError: Boolean
)

@Composable
fun MainScreen(
    config: ConnectionConfig = ConnectionConfig(),
    preferences: ConnectionPreferences = ConnectionPreferences(LocalContext.current),
    isDarkTheme: Boolean = true,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val logItems by viewModel.logItems.collectAsState()
    val errorCount by viewModel.errorCount.collectAsState()

    var showConnectionDialog by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var showCodingBar by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var previousWorkingState by remember { mutableStateOf(false) }

    // Animation trượt mượt mà khi vuốt chuyển màn hình
    val swipeOffsetX = remember { Animatable(0f) }
    val swipeAlpha = remember { Animatable(1f) }

    // Khởi tạo tab đầu tiên khi nạp cấu hình URL
    LaunchedEffect(config.httpUrl) {
        viewModel.initInitialTab(config.httpUrl)
    }

    // Xử lý mở thẳng vào phiên khi người dùng bấm vào Thông báo phiên trên Android
    val activity = context as? android.app.Activity
    LaunchedEffect(activity?.intent) {
        val targetUrl = activity?.intent?.getStringExtra("EXTRA_TARGET_URL")
        if (!targetUrl.isNullOrBlank()) {
            activity.intent.removeExtra("EXTRA_TARGET_URL")
            val targetTab = viewModel.handleNotificationIntent(targetUrl)
            webViewInstance?.loadUrl(targetTab.url)
        }
    }

    // Lắng nghe cập nhật phiên Realtime ngầm từ Service
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val title = intent?.getStringExtra("title") ?: return
                val isWorking = intent.getBooleanExtra("isWorking", false)
                val convoId = intent.getStringExtra("convoId") ?: ""

                viewModel.handleSessionUpdate(convoId, title, isWorking)
            }
        }
        val filter = IntentFilter("com.example.agyremote.SESSION_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Tự động duy trì Foreground Service để nhận thông báo real-time khi bật thông báo
    LaunchedEffect(config.hostIp, config.port, config.notificationsEnabled) {
        if (config.notificationsEnabled && config.hostIp.isNotBlank()) {
            try {
                AgyNotificationService.start(context, config.hostIp, config.port)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                AgyNotificationService.stop(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var lastNotifiedTime by remember { mutableLongStateOf(0L) }
    var lastNotifiedConclusion by remember { mutableStateOf("") }

    // Bắn Notification khi hoàn thành tác vụ (Chỉ hiện thông báo kết luận cuối cùng)
    fun notifyTaskCompleted(taskTitle: String, conclusion: String = "") {
        val now = System.currentTimeMillis()
        val textToNotify = if (conclusion.isNotBlank()) conclusion else "Đã hoàn thành: $taskTitle"

        // Chỉ hiện thông báo kết luận cuối cùng, bỏ qua log lệnh nội bộ
        if (textToNotify.contains("Created At:") || textToNotify.contains("Completed At:") || textToNotify.contains("task-")) {
            return
        }

        // Chống bắn thông báo trùng lặp hoặc bắn quá dày trong vòng 6 giây
        if (now - lastNotifiedTime < 6000L && textToNotify == lastNotifiedConclusion) {
            return
        }
        if (now - lastNotifiedTime < 4000L) {
            return
        }
        lastNotifiedTime = now
        lastNotifiedConclusion = textToNotify

        try {
            val title = if (taskTitle.isNotBlank() && taskTitle != "Cuộc trò chuyện") "✅ $taskTitle" else "✅ Hoàn tất câu trả lời"
            Toast.makeText(context, "$title\n$textToNotify", Toast.LENGTH_SHORT).show()

            AgyNotificationService.showPushNotification(
                context,
                title,
                textToNotify
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Xử lý sự kiện thay đổi trạng thái Working từ WebView
    fun handleWorkingStatusChanged(isWorking: Boolean, path: String = "") {
        viewModel.updateTabWorkingByPath(path, isWorking)
        // Chỉ cập nhật cờ UI trạng thái tab, KHÔNG bắn thông báo tại đây để tránh spam giữa các bước tool call.
        // Thông báo kết luận cuối cùng được kích hoạt bởi onTaskDone sau 3s debounce ổn định.
        previousWorkingState = isWorking
    }

    // Mở rộng toàn bộ các thẻ hành động (Tool Actions)
    fun expandAllActions() {
        webViewInstance?.evaluateJavascript(AgyActionScript.getForceExpandScript()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.makeText(context, "⚡ Đã mở rộng toàn bộ thẻ Action", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher chụp ảnh trực tiếp từ Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val outputDir = java.io.File(context.cacheDir, "agy_uploads").apply { mkdirs() }
                val outputFile = java.io.File(outputDir, "cam_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(outputFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile
                )
                val result = ImageOptimizer.getBase64Image(context, uri)
                if (result != null) {
                    val (base64, mimeType) = result
                    val fileName = "cam_${System.currentTimeMillis()}.jpg"
                    withContext(Dispatchers.Main) {
                        injectImageIntoWebView(webViewInstance, base64, mimeType, fileName)
                        Toast.makeText(context, "⚡ Đã đính kèm ảnh chụp camera!", Toast.LENGTH_SHORT).show()
                        viewModel.addLog("CAMERA", "Đã đính kèm ảnh camera vào phiên chat", false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    viewModel.addLog("CAM_ERR", "Lỗi nạp ảnh camera: ${e.message}", true)
                }
            }
        }
    }

    // Launcher chọn ảnh trực tiếp từ Thư viện / Thiết bị Android
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult

        scope.launch(Dispatchers.IO) {
            var successCount = 0
            uris.forEachIndexed { index, uri ->
                try {
                    val optimizedUri = ImageOptimizer.optimizeImage(context, uri) ?: uri
                    val result = ImageOptimizer.getBase64Image(context, optimizedUri)
                        ?: ImageOptimizer.getBase64Image(context, uri)

                    if (result != null) {
                        val (base64, mimeType) = result
                        val fileName = "img_${System.currentTimeMillis()}_$index.jpg"
                        withContext(Dispatchers.Main) {
                            injectImageIntoWebView(webViewInstance, base64, mimeType, fileName)
                            successCount++
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        viewModel.addLog("IMG_ERR", "Lỗi nạp ảnh: ${e.message}", true)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (successCount > 0) {
                    Toast.makeText(context, "⚡ Đã đính kèm $successCount ảnh!", Toast.LENGTH_SHORT).show()
                    viewModel.addLog("UPLOAD", "Đã đính kèm $successCount ảnh vào phiên chat", false)
                }
            }
        }
    }

    // Xử lý File / Media Picker cho WebView
    var activeFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = activeFilePathCallback
        activeFilePathCallback = null

        if (result.resultCode != android.app.Activity.RESULT_OK || result.data == null) {
            callback?.onReceiveValue(null)
            return@rememberLauncherForActivityResult
        }

        val intentData = result.data
        scope.launch(Dispatchers.IO) {
            try {
                val uris = mutableListOf<Uri>()
                intentData?.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        uris.add(clip.getItemAt(i).uri)
                    }
                } ?: intentData?.data?.let { uris.add(it) }

                if (uris.isEmpty()) {
                    withContext(Dispatchers.Main) { callback?.onReceiveValue(null) }
                    return@launch
                }

                // Xử lý tối ưu hóa ảnh và tạo content:// URI hợp lệ qua FileProvider
                val finalUris = uris.map { rawUri ->
                    val isImage = try {
                        val mime = context.contentResolver.getType(rawUri) ?: ""
                        mime.startsWith("image/") || rawUri.toString().lowercase().let {
                            it.contains(".jpg") || it.contains(".jpeg") || it.contains(".png") || it.contains(".webp")
                        }
                    } catch (e: Exception) {
                        false
                    }

                    if (isImage) {
                        ImageOptimizer.optimizeImage(context, rawUri) ?: rawUri
                    } else {
                        rawUri
                    }
                }

                withContext(Dispatchers.Main) {
                    finalUris.forEach { uri ->
                        try {
                            context.grantUriPermission(
                                context.packageName,
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {}
                    }
                    callback?.onReceiveValue(finalUris.toTypedArray())
                    viewModel.addLog("UPLOAD", "Đã chọn ${finalUris.size} tệp thành công", false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val fallback = WebChromeClient.FileChooserParams.parseResult(result.resultCode, intentData)
                    callback?.onReceiveValue(fallback)
                    viewModel.addLog("UPLOAD_ERR", "Lỗi xử lý file: ${e.message}", true)
                }
            }
        }
    }

    // Xử lý Clipboard Image
    fun getClipboardImageUri(): Uri? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).uri
        }
        return null
    }

    var clipboardImageUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(Unit) {
        clipboardImageUri = getClipboardImageUri()
    }

    fun pasteClipboardImage() {
        val uri = clipboardImageUri ?: getClipboardImageUri()
        if (uri == null) {
            Toast.makeText(context, "Clipboard không có hình ảnh", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Tối ưu hóa ảnh từ clipboard
                val optimizedUri = ImageOptimizer.optimizeImage(context, uri) ?: uri
                val result = ImageOptimizer.getBase64Image(context, optimizedUri)
                    ?: ImageOptimizer.getBase64Image(context, uri)

                if (result == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Không thể đọc ảnh từ clipboard", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val (base64, mimeType) = result
                withContext(Dispatchers.Main) {
                    // 2. Bơm trực tiếp vào webview qua JavaScript
                    injectImageIntoWebView(webViewInstance, base64, mimeType, "clipboard_${System.currentTimeMillis()}.jpg")
                    Toast.makeText(context, "⚡ Đã dán ảnh thành công!", Toast.LENGTH_SHORT).show()
                    viewModel.addLog("CLIPBOARD", "Đã dán ảnh từ clipboard vào phiên chat", false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi đọc ảnh clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
                    viewModel.addLog("CLIPBOARD_ERR", "Lỗi dán ảnh: ${e.message}", true)
                }
            }
        }
    }

    // Điều hướng Native
    fun toggleAgySidebar(webView: WebView?) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            swipeOffsetX.animateTo(-50f, animationSpec = tween(70, easing = FastOutLinearInEasing))
            swipeOffsetX.animateTo(0f, animationSpec = tween(140, easing = FastOutSlowInEasing))
        }
        val js = """
            (function() {
                const currentPath = window.location.pathname;
                if (currentPath.startsWith('/c/') || currentPath.startsWith('/history')) {
                    const backBtn = document.querySelector('button[aria-label="Back" i], button[aria-label="Quay lại" i], header button:first-child');
                    if (backBtn) { backBtn.click(); return; }
                    const homeLink = document.querySelector('a[href="/"], a[href="#/"]');
                    if (homeLink) { homeLink.click(); return; }
                    window.location.href = '/';
                } else {
                    const lastConvo = window.__agyLastActiveConvo || localStorage.getItem('agy_last_active_convo');
                    if (lastConvo && lastConvo.startsWith('/c/')) {
                        window.location.href = lastConvo;
                        return;
                    }
                    const firstConvo = document.querySelector('a[href^="/c/"], div[class*="conversation-item"], [data-testid*="conversation-item"]');
                    if (firstConvo) { firstConvo.click(); }
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    fun navigateToProjects(webView: WebView?) {
        val js = """
            (function() {
                const homeLink = document.querySelector('a[href="/"], a[href="#/"]');
                if (homeLink) { homeLink.click(); return; }
                if (window.location.pathname !== '/') { window.location.href = '/'; }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    fun submitAuthCodeToHost(code: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://${config.hostIp}:${config.port}/__agy_oauth_code")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val body = """{"code":"$code"}"""
                OutputStreamWriter(conn.outputStream).use { it.write(body); it.flush() }

                val respCode = conn.responseCode
                withContext(Dispatchers.Main) {
                    if (respCode == 200) {
                        viewModel.addLog("AUTH", "Đã gửi mã xác thực lên Host thành công", false)
                        snackbarHostState.showSnackbar("Đăng nhập thành công! Đang tải lại...")
                        delay(1200)
                        webViewInstance?.loadUrl(config.httpUrl)
                    } else {
                        viewModel.addLog("AUTH_ERR", "Host trả về lỗi: $respCode", true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    viewModel.addLog("AUTH_ERR", "Lỗi gửi Auth code: ${e.message}", true)
                }
            }
        }
    }

    val currentTab = tabs.find { it.id == activeTabId }

    // Xử lý nút Back của Android
    BackHandler(enabled = true) {
        val currentUrl = currentTab?.url ?: ""
        if (currentUrl.contains("/c/")) {
            // Đang ở trong phiên chat -> lùi về màn hình dự án / danh sách phiên
            navigateToProjects(webViewInstance)
        } else if (tabs.size > 1) {
            // Đang ở màn hình dự án mà có nhiều tab -> đóng tab hiện tại
            val nextTab = viewModel.closeTab(activeTabId)
            if (nextTab != null) {
                navigateToAgyUrl(webViewInstance, nextTab.url, config.httpUrl)
            }
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            activity?.moveTaskToBack(true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0F172A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. THANH STATUSBAR 1 (TOP STATUS BAR)
            TopStatusBar(
                hostIp = config.hostIp,
                port = config.port,
                isWorking = currentTab?.isWorking == true,
                isCollapsed = isFullscreen,
                errorCount = errorCount,
                clipboardImageUri = clipboardImageUri,
                isCodingBarVisible = showCodingBar,
                onOpenCamera = { cameraLauncher.launch(null) },
                onOpenImagePicker = { imagePickerLauncher.launch("image/*") },
                onPasteClipboardImage = { pasteClipboardImage() },
                onToggleCodingBar = { showCodingBar = !showCodingBar },
                onShowLogs = { showLogsSheet = true },
                onRefresh = {
                    errorMessage = null
                    isLoading = true
                    webViewInstance?.reload()
                },
                onExpandActions = { expandAllActions() },
                onOpenSettings = { showConnectionDialog = true },
                onToggleCollapse = { isFullscreen = true },
                onSendPrompt = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    webViewInstance?.evaluateJavascript("window.__agyTriggerSend && window.__agyTriggerSend();", null)
                },
                onCancelPrompt = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    webViewInstance?.evaluateJavascript("window.__agyTriggerStop && window.__agyTriggerStop();", null)
                },
                modifier = Modifier.statusBarsPadding()
            )

            // 2. THANH STATUSBAR 2 (TAB BAR)
            TabBar(
                tabs = tabs,
                activeTabId = activeTabId,
                isFullscreen = isFullscreen,
                onSelectTab = { tab ->
                    if (activeTabId != tab.id) {
                        viewModel.selectTab(tab)
                        navigateToAgyUrl(webViewInstance, tab.url, config.httpUrl)
                    }
                },
                onCloseTab = { tabId ->
                    val nextTab = viewModel.closeTab(tabId)
                    if (nextTab != null) {
                        navigateToAgyUrl(webViewInstance, nextTab.url, config.httpUrl)
                    }
                },
                onAddTab = {
                    val newTab = viewModel.createNewTab(config.httpUrl)
                    navigateToAgyUrl(webViewInstance, newTab.url, config.httpUrl)
                },
                onToggleSidebar = { toggleAgySidebar(webViewInstance) },
                onRestoreFullscreen = { isFullscreen = false }
            )

            // 2.1 THANH PHÍM TẮT LẬP TRÌNH NHANH (ACCESSORY CODING BAR)
            if (showCodingBar) {
                AccessoryCodingBar(
                    onInsertText = { text ->
                        val escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                        webViewInstance?.evaluateJavascript("window.__agyInsertText && window.__agyInsertText('$escaped');", null)
                    },
                    onInsertNewline = {
                        webViewInstance?.evaluateJavascript("window.__agyInsertText && window.__agyInsertText('\\n');", null)
                    },
                    onClearInput = {
                        webViewInstance?.evaluateJavascript("window.__agyClearInput && window.__agyClearInput();", null)
                    }
                )
            }

            // 3. VÙNG HIỂN THỊ NỘI DUNG WEBVIEW DUY NHẤT (ĐIỀU HƯỚNG SPA SIÊU TỐC 0MS - KHÔNG RELOAD - DISK CACHE)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = swipeOffsetX.value
                        alpha = swipeAlpha.value
                    }
            ) {
                AgyWebView(
                    url = config.httpUrl,
                    isVisible = true,
                    modifier = Modifier.fillMaxSize(),
                    isDarkTheme = isDarkTheme,
                    onPageStarted = {
                        isLoading = true
                        errorMessage = null
                    },
                    onPageFinished = { currentUrl ->
                        isLoading = false
                        errorMessage = null
                        viewModel.addLog("NAV", "Hoàn tất tải: $currentUrl", false)
                    },
                    onTitleReceived = { title ->
                        viewModel.addLog("TITLE", "Trang: $title", false)
                    },
                    onWorkingStatusChanged = { isWorking, path ->
                        handleWorkingStatusChanged(isWorking, path)
                    },
                    onTaskDone = { taskTitle, conclusion ->
                        notifyTaskCompleted(taskTitle.ifBlank { "Cuộc trò chuyện" }, conclusion)
                    },
                    onNavigationChanged = { screen ->
                        viewModel.addLog("NAV_VIEW", "Chuyển màn hình: $screen", false)
                    },
                    onErrorReceived = { error ->
                        isLoading = false
                        errorMessage = error
                        viewModel.addLog("ERR", error, true)
                    },
                    onLogReceived = { tag, msg, isError ->
                        viewModel.addLog(tag, msg, isError)
                    },
                    onAuthCodeCaptured = { code ->
                        submitAuthCodeToHost(code)
                    },
                    onRequestFileChooser = { callback, params ->
                        activeFilePathCallback?.onReceiveValue(null)
                        activeFilePathCallback = callback

                        val intent = try {
                            params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                        } catch (e: Exception) {
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                        }
                        filePickerLauncher.launch(intent)
                    },
                    onSessionInfoReceived = { path, title ->
                        viewModel.updateActiveTabSessionInfo(path, title, config.httpUrl)
                    },
                    onOpenImagePicker = {
                        imagePickerLauncher.launch("image/*")
                    },
                    onWebViewCreated = { webView ->
                        webViewInstance = webView
                    }
                )

                // Banner báo lỗi kết nối
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Không thể tải Antigravity",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    webViewInstance?.reload()
                                }
                            ) {
                                Text("Thử lại")
                            }
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng thông báo")
                            }
                        }
                    }
                }

                // Vòng xoay tải trang
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }

    // Modal BottomSheet hiển thị Log ADB
    if (showLogsSheet) {
        LogsBottomSheet(
            logItems = logItems,
            onClearLogs = { viewModel.clearLogs() },
            onDismiss = { showLogsSheet = false }
        )
    }

    // Hộp thoại cấu hình kết nối LAN
    if (showConnectionDialog) {
        ConnectionDialog(
            currentConfig = config,
            onDismiss = { showConnectionDialog = false },
            onConnect = { ip, port, autoReconnect, notificationsEnabled ->
                scope.launch {
                    preferences.saveHost(ip, port)
                    preferences.setAutoReconnect(autoReconnect)
                    preferences.setNotificationsEnabled(notificationsEnabled)
                    showConnectionDialog = false
                    val newUrl = "http://$ip:$port"
                    webViewInstance?.loadUrl(newUrl)
                }
            }
        )
    }
}
