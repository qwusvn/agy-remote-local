package com.example.agyremote.ui

import android.Manifest
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import android.provider.Settings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.agyremote.MainActivity
import com.example.agyremote.R
import com.example.agyremote.data.ConnectionConfig
import com.example.agyremote.data.ConnectionPreferences
import com.example.agyremote.media.ImageOptimizer
import com.example.agyremote.service.AgyNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BrowserTab(
    val id: String,
    val title: String,
    val url: String,
    val isWorking: Boolean = false
)

data class LogItem(
    val time: String,
    val tag: String,
    val message: String,
    val isError: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    config: ConnectionConfig = ConnectionConfig(),
    preferences: ConnectionPreferences = ConnectionPreferences(LocalContext.current),
    isDarkTheme: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showConnectionDialog by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Quản lý Đa Tab trình duyệt
    val tabs = remember { mutableStateListOf<BrowserTab>() }
    var activeTabId by remember { mutableStateOf("") }
    var previousWorkingState by remember { mutableStateOf(false) }

    // Khởi tạo tab đầu tiên khi mở app
    LaunchedEffect(config.httpUrl) {
        if (tabs.isEmpty() && config.httpUrl.isNotBlank()) {
            val initialTab = BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Antigravity",
                url = config.httpUrl
            )
            tabs.add(initialTab)
            activeTabId = initialTab.id
        }
    }

    fun createNewTab(url: String = config.httpUrl) {
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            title = "Tab ${tabs.size + 1}",
            url = url
        )
        tabs.add(newTab)
        activeTabId = newTab.id
        webViewInstance?.loadUrl(url)
    }

    fun closeTab(tabId: String) {
        if (tabs.size > 1) {
            val idx = tabs.indexOfFirst { it.id == tabId }
            if (idx >= 0) {
                tabs.removeAt(idx)
                if (activeTabId == tabId) {
                    val nextTab = tabs.getOrNull(idx) ?: tabs.last()
                    activeTabId = nextTab.id
                    webViewInstance?.loadUrl(nextTab.url)
                }
            }
        }
    }

    fun selectTab(tab: BrowserTab) {
        if (activeTabId != tab.id) {
            activeTabId = tab.id
            webViewInstance?.loadUrl(tab.url)
        }
    }

    // Phát chuông, rung và bắn Notification khi hoàn thành tác vụ
    fun notifyTaskCompleted(taskTitle: String) {
        try {
            // 1. Rung điện thoại
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(450)
            }

            // 2. Phát âm thanh chuông thông báo
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, alertUri)
            ringtone?.play()

            // 3. Bắn Notification Heads-up lên Android
            AgyNotificationService.showPushNotification(
                context,
                "🎉 Agent đã hoàn thành tác vụ!",
                taskTitle
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Xử lý sự kiện thay đổi trạng thái Working từ WebView
    fun handleWorkingStatusChanged(isWorking: Boolean) {
        val idx = tabs.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) {
            tabs[idx] = tabs[idx].copy(isWorking = isWorking)
        }

        // Nếu vừa chuyển từ đang làm việc sang hoàn tất -> Báo chuông / rung
        if (previousWorkingState && !isWorking) {
            val title = tabs.getOrNull(idx)?.title ?: "Cuộc trò chuyện"
            notifyTaskCompleted(title)
            scope.launch {
                snackbarHostState.showSnackbar("🎉 Agent đã hoàn thành tác vụ!")
            }
        }
        previousWorkingState = isWorking
    }

    // Danh sách lưu log
    val logItems = remember { mutableStateListOf<LogItem>() }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    fun addLog(tag: String, msg: String, isError: Boolean) {
        val item = LogItem(
            time = timeFormat.format(Date()),
            tag = tag,
            message = msg,
            isError = isError
        )
        if (logItems.size > 200) {
            logItems.removeAt(0)
        }
        logItems.add(item)
    }

    val errorCount = logItems.count { it.isError }

    // Quản lý callback chọn file cho WebView
    var activeFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { selectedUri ->
        val callback = activeFilePathCallback
        activeFilePathCallback = null

        if (selectedUri == null) {
            callback?.onReceiveValue(null)
        } else {
            scope.launch {
                val optimizedUri = ImageOptimizer.optimizeImage(context, selectedUri)
                if (optimizedUri != null) {
                    callback?.onReceiveValue(arrayOf(optimizedUri))
                } else {
                    callback?.onReceiveValue(arrayOf(selectedUri))
                }
            }
        }
    }

    // Quản lý Clipboard ảnh
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var clipboardImageUri by remember { mutableStateOf<Uri?>(null) }

    fun checkClipboard(): Uri? {
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount > 0) {
            val item = clip.getItemAt(0)
            val uri = item.uri
            if (uri != null) {
                val mime = context.contentResolver.getType(uri)
                if (mime?.startsWith("image/") == true) {
                    return uri
                }
            }
        }
        return null
    }

    LaunchedEffect(Unit) {
        while (true) {
            clipboardImageUri = checkClipboard()
            delay(3000L)
        }
    }

    fun pasteClipboardImage() {
        val uri = clipboardImageUri ?: checkClipboard()
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val cacheFile = File(context.cacheDir, "clip_paste_${System.currentTimeMillis()}.png")
                    inputStream?.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val fileUri = Uri.fromFile(cacheFile)

                    if (activeFilePathCallback != null) {
                        activeFilePathCallback?.onReceiveValue(arrayOf(fileUri))
                        activeFilePathCallback = null
                        snackbarHostState.showSnackbar("✅ Đã đính kèm ảnh từ clipboard!")
                    } else {
                        val clickAttachJs = """
                            (function() {
                                const attachBtn = document.querySelector('button[aria-label*="attach" i], button[aria-label*="upload" i], input[type="file"]');
                                if (attachBtn) { attachBtn.click(); }
                            })();
                        """.trimIndent()
                        webViewInstance?.evaluateJavascript(clickAttachJs, null)
                        snackbarHostState.showSnackbar("📋 Đã sao chép ảnh! Nhấn đính kèm (+) trong chat để dán.")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("⚠️ Lỗi đọc ảnh clipboard: ${e.message}")
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("ℹ️ Không tìm thấy ảnh trong bộ nhớ tạm (Clipboard)")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(config.hostIp, config.port, config.notificationsEnabled) {
        try {
            if (config.notificationsEnabled && config.hostIp.isNotBlank()) {
                AgyNotificationService.start(context, config.hostIp, config.port)
            } else {
                AgyNotificationService.stop(context)
            }
        } catch (e: Exception) {
            addLog("NOTIF_ERR", e.message ?: "Lỗi service", true)
        }
    }

    // Hàm mở luồng Đổi tài khoản Google qua Bridge
    fun startSwitchAccountFlow() {
        scope.launch {
            snackbarHostState.showSnackbar("Đang mở trang đăng nhập Google...")
            addLog("AUTH", "Khởi động luồng đổi tài khoản Google qua Bridge", false)
            // Xóa sạch cookie để Google bắt buộc hiển thị màn hình chọn tài khoản
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val authUrl = "http://${config.hostIp}:${config.port}/auth/google"
            webViewInstance?.loadUrl(authUrl)
        }
    }

    // Tự động nạp Authorization Code bắt được từ Google OAuth lên máy tính
    fun submitAuthCodeToHost(code: String) {
        scope.launch {
            snackbarHostState.showSnackbar("Đang tự động nạp mã xác thực vào máy tính...")
            addLog("AUTO_AUTH", "Bắt đầu gửi code lên http://${config.hostIp}:${config.port}/api/auth_code", false)
            val success = withContext(Dispatchers.IO) {
                try {
                    val url = URL("http://${config.hostIp}:${config.port}/api/auth_code")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000

                    val jsonBody = """{"code":"$code"}"""
                    OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

                    val responseCode = conn.responseCode
                    conn.disconnect()
                    responseCode in 200..299
                } catch (e: Exception) {
                    addLog("AUTO_AUTH_ERR", e.message ?: "Lỗi gửi code", true)
                    false
                }
            }

            if (success) {
                snackbarHostState.showSnackbar("✅ Đã đổi tài khoản thành công! Đang tải lại Antigravity...")
                addLog("AUTO_AUTH_OK", "Máy tính đã nhận mã xác thực và restart server. Đang tải lại...", false)
                delay(2000L)
                webViewInstance?.loadUrl(config.httpUrl)
            } else {
                snackbarHostState.showSnackbar("⚠️ Không thể tự nạp mã lên cổng ${config.port}. Bạn có thể kiểm tra log.")
            }
        }
    }

    // Theme Colors
    val barBgColor = if (isDarkTheme) Color(0xFF13141C) else Color(0xFFF1F5F9)
    val tabRowBgColor = if (isDarkTheme) Color(0xFF1A1C28) else Color(0xFFE2E8F0)
    val tabActiveBgColor = if (isDarkTheme) Color(0xFF252838) else Color(0xFFFFFFFF)
    val tabInactiveBgColor = Color.Transparent
    val textColor = if (isDarkTheme) Color.White else Color(0xFF0F172A)
    val currentTabIsWorking = tabs.firstOrNull { it.id == activeTabId }?.isWorking == true

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // DÒNG 1: TRẠNG THÁI & CÔNG CỤ (30dp) - Có thể thu gọn khi người dùng bấm nút ^
            AnimatedVisibility(visible = !isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(30.dp)
                        .background(barBgColor)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Bên trái: Trạng thái kết nối & Chỉ báo Working
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentTabIsWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.8.dp,
                                color = Color(0xFFF59E0B)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                        }

                        Text(
                            text = "AGY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        Text(
                            text = "${config.hostIp}:${config.port}",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Bên phải: Các nút công cụ thao tác
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Nút Dán ảnh từ Clipboard
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { pasteClipboardImage() },
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (clipboardImageUri != null) {
                                        Badge(
                                            containerColor = Color(0xFF22C55E),
                                            modifier = Modifier.size(5.dp)
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = "Gửi ảnh Clipboard",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (clipboardImageUri != null) Color(0xFF22C55E) else textColor.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Nút xem Log ADB
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { showLogsSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (errorCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(5.dp)
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = "Xem Log ADB",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (errorCount > 0) MaterialTheme.colorScheme.error else textColor.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Nút Tải lại trang
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable {
                                    errorMessage = null
                                    isLoading = true
                                    webViewInstance?.reload()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Tải lại trang",
                                modifier = Modifier.size(14.dp),
                                tint = textColor.copy(alpha = 0.65f)
                            )
                        }

                        // Nút Đổi tài khoản Google
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { startSwitchAccountFlow() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Đổi tài khoản Google",
                                modifier = Modifier.size(15.dp),
                                tint = textColor.copy(alpha = 0.75f)
                            )
                        }

                        // Nút Cài đặt
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { showConnectionDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Cài đặt kết nối",
                                modifier = Modifier.size(14.dp),
                                tint = textColor.copy(alpha = 0.65f)
                            )
                        }

                        // Nút Thu gọn Dòng 1
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { isFullscreen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Thu gọn Dòng 1",
                                modifier = Modifier.size(16.dp),
                                tint = textColor.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }

            // DÒNG 2: THANH STATUSBAR 2 (TAB BAR) - CỐ ĐỊNH HOÀN TOÀN, KHÔNG THỂ ẨN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .height(44.dp)
                    .background(tabRowBgColor)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Nút Menu Toggle Dự án/phiên - Phiên đang mở (Rộng 42dp, icon 22dp không bị cấn tay)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            toggleAgySidebar(webViewInstance)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Chuyển đổi Dự án / Phiên đang mở",
                        modifier = Modifier.size(22.dp),
                        tint = textColor.copy(alpha = 0.9f)
                    )
                }

                // Nếu Dòng 1 đang bị ẩn, hiện nút nhỏ để khôi phục Dòng 1
                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { isFullscreen = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Hiện lại thanh trạng thái",
                            modifier = Modifier.size(16.dp),
                            tint = textColor.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 2. Danh sách các Tab (Tối đa 3 tab/màn hình, từ tab thứ 4 cần vuốt ngang)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val availableWidth = maxWidth - 44.dp
                    val singleTabWidth = (availableWidth / 3f).coerceAtLeast(80.dp)

                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            val isActive = tab.id == activeTabId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) tabActiveBgColor else tabInactiveBgColor,
                                modifier = Modifier
                                    .width(singleTabWidth)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectTab(tab) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (tab.isWorking) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(11.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFFF59E0B)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isActive) Color(0xFF22C55E) else Color.Gray.copy(alpha = 0.45f))
                                            )
                                        }

                                        Text(
                                            text = if (tab.isWorking) "${tab.title} ⏳" else tab.title,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isActive) textColor else textColor.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (tabs.size > 1) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .clickable { closeTab(tab.id) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Đóng tab",
                                                modifier = Modifier.size(12.dp),
                                                tint = textColor.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Nút thêm tab mới (+) rộng rãi không bị cấn tay
                        item {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { createNewTab() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Mở tab mới",
                                    modifier = Modifier.size(20.dp),
                                    tint = textColor.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // VÙNG HIỂN THỊ WEBVIEW (Đã loại bỏ mọi Gesture đè, cuộn 120Hz mượt mà tuyệt đối)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AgyWebView(
                    url = config.httpUrl,
                    modifier = Modifier.fillMaxSize(),
                    isDarkTheme = isDarkTheme,
                    onPageStarted = { currentUrl ->
                        isLoading = true
                        addLog("NAV", "Bắt đầu tải: $currentUrl", false)
                    },
                    onPageFinished = { currentUrl ->
                        isLoading = false
                        errorMessage = null
                        addLog("NAV", "Hoàn tất tải: $currentUrl", false)
                    },
                    onTitleReceived = { title ->
                        val idx = tabs.indexOfFirst { it.id == activeTabId }
                        if (idx >= 0 && title.isNotBlank() && title != "Antigravity 2.0" && title != "about:blank") {
                            tabs[idx] = tabs[idx].copy(title = title)
                        }
                    },
                    onWorkingStatusChanged = { isWorking ->
                        handleWorkingStatusChanged(isWorking)
                    },
                    onNavigationChanged = { screen ->
                        scope.launch {
                            val text = when (screen) {
                                "projects" -> "📂 Màn hình Dự án / Phiên"
                                "active_convo" -> "💬 Phiên đang hoạt động"
                                "history" -> "📜 Lịch sử các phiên (History)"
                                else -> ""
                            }
                            if (text.isNotEmpty()) {
                                snackbarHostState.showSnackbar(text)
                            }
                        }
                    },
                    onErrorReceived = { error ->
                        isLoading = false
                        errorMessage = error
                        addLog("ERR", error, true)
                    },
                    onLogReceived = { tag, msg, isError ->
                        addLog(tag, msg, isError)
                    },
                    onAuthCodeCaptured = { code ->
                        submitAuthCodeToHost(code)
                    },
                    onRequestFileChooser = { callback ->
                        activeFilePathCallback = callback
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onWebViewCreated = { webView ->
                        webViewInstance = webView
                    }
                )

                // Banner lỗi
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.TopCenter),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 6.dp
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

    // Modal BottomSheet hiển thị Log ADB thời gian thực
    if (showLogsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val listState = rememberLazyListState()

        LaunchedEffect(logItems.size) {
            if (logItems.isNotEmpty()) {
                listState.animateScrollToItem(logItems.size - 1)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showLogsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 Log ADB / Network (${logItems.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row {
                        IconButton(onClick = {
                            val allText = logItems.joinToString("\n") { "[${it.time}] [${it.tag}] ${it.message}" }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AGY_LOGS", allText))
                            Toast.makeText(context, "Đã sao chép toàn bộ log", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép tất cả")
                        }
                        IconButton(onClick = { logItems.clear() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa log")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A)
                ) {
                    if (logItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Chưa có log nào", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            items(logItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.time,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(80.dp)
                                    )
                                    Text(
                                        text = "[${item.tag}]",
                                        fontSize = 11.sp,
                                        color = if (item.isError) Color(0xFFF87171) else Color(0xFF38BDF8),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(90.dp)
                                    )
                                    Text(
                                        text = item.message,
                                        fontSize = 12.sp,
                                        color = if (item.isError) Color(0xFFFCA5A5) else Color(0xFFE2E8F0),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConnectionDialog) {
        ConnectionDialog(
            currentConfig = config,
            onDismiss = { showConnectionDialog = false },
            onThemeChange = { theme ->
                scope.launch {
                    preferences.setTheme(theme)
                }
            },
            onSwitchAccount = {
                startSwitchAccountFlow()
            },
            onConnect = { newIp, newPort, newAutoReconnect, newNotifications ->
                scope.launch {
                    preferences.saveHost(newIp, newPort)
                    preferences.setAutoReconnect(newAutoReconnect)
                    preferences.setNotificationsEnabled(newNotifications)
                    errorMessage = null
                    val newUrl = "http://$newIp:$newPort"
                    webViewInstance?.loadUrl(newUrl)
                    if (tabs.isNotEmpty()) {
                        val idx = tabs.indexOfFirst { it.id == activeTabId }
                        if (idx >= 0) {
                            tabs[idx] = tabs[idx].copy(url = newUrl)
                        }
                    }
                    showConnectionDialog = false
                }
            }
        )
    }
}
