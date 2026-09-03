package com.example.agyremote.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.agyremote.data.ConnectionConfig
import com.example.agyremote.data.ConnectionPreferences
import com.example.agyremote.media.ImageOptimizer
import com.example.agyremote.service.AgyNotificationService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { ConnectionPreferences(context) }
    val config by preferences.configFlow.collectAsState(initial = ConnectionConfig())

    var showConnectionDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Quản lý callback chọn file cho WebView
    var activeFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // Launcher mở Native Photo Picker của Android
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { selectedUri ->
        val callback = activeFilePathCallback
        activeFilePathCallback = null

        if (selectedUri == null) {
            callback?.onReceiveValue(null)
        } else {
            scope.launch {
                // Tự động nén và chuẩn hóa ảnh trước khi gửi vào WebView
                val optimizedUri = ImageOptimizer.optimizeImage(context, selectedUri)
                if (optimizedUri != null) {
                    callback?.onReceiveValue(arrayOf(optimizedUri))
                } else {
                    callback?.onReceiveValue(arrayOf(selectedUri))
                }
            }
        }
    }

    // Xin quyền thông báo trên Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Quyền thông báo đã được phản hồi
    }

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

    // Quản lý Foreground Notification Service khi cấu hình thay đổi
    LaunchedEffect(config.hostIp, config.port, config.notificationsEnabled) {
        if (config.notificationsEnabled && config.hostIp.isNotBlank()) {
            AgyNotificationService.start(context, config.hostIp, config.port)
        } else {
            AgyNotificationService.stop(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AGY Remote",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${config.hostIp}:${config.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        errorMessage = null
                        webViewInstance?.reload()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Tải lại trang")
                    }
                    IconButton(onClick = { showConnectionDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Cài đặt kết nối")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Thanh tiến trình tải trang
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            if (errorMessage != null) {
                // Giao diện khi mất kết nối tới máy chủ AGY
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Không thể kết nối tới Antigravity",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Vui lòng kiểm tra máy tính đã bật Antigravity 2.0 và điện thoại cùng mạng WiFi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = { showConnectionDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đổi địa chỉ IP")
                        }
                        Button(onClick = {
                            errorMessage = null
                            webViewInstance?.loadUrl(config.httpUrl)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thử lại")
                        }
                    }
                }
            } else {
                // Thành phần hiển thị Web AGY 2.0
                AgyWebView(
                    url = config.httpUrl,
                    modifier = Modifier.fillMaxSize(),
                    onPageStarted = {
                        isLoading = true
                    },
                    onPageFinished = {
                        isLoading = false
                        errorMessage = null
                    },
                    onErrorReceived = { error ->
                        isLoading = false
                        errorMessage = error
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
            }
        }
    }

    if (showConnectionDialog) {
        ConnectionDialog(
            currentConfig = config,
            onDismiss = { showConnectionDialog = false },
            onConnect = { newIp, newPort, newAutoReconnect, newNotifications ->
                scope.launch {
                    preferences.saveHost(newIp, newPort)
                    preferences.setAutoReconnect(newAutoReconnect)
                    preferences.setNotificationsEnabled(newNotifications)
                    errorMessage = null
                    webViewInstance?.loadUrl("http://$newIp:$newPort")
                    showConnectionDialog = false
                }
            }
        )
    }
}
