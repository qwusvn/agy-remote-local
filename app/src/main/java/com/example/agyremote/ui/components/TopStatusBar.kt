package com.example.agyremote.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentPaste

/**
 * Thanh trạng thái phía trên (Statusbar 1).
 * Quản lý thông tin kết nối và thanh công cụ thao tác nhanh.
 */
@Composable
fun TopStatusBar(
    hostIp: String,
    port: Int,
    isWorking: Boolean,
    isCollapsed: Boolean,
    errorCount: Int,
    clipboardImageUri: Uri?,
    onOpenImagePicker: () -> Unit,
    onPasteClipboardImage: () -> Unit,
    onShowLogs: () -> Unit,
    onRefresh: () -> Unit,
    onExpandActions: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = Color(0xFF0F172A)
    val textColor = Color(0xFFF8FAFC)

    AnimatedVisibility(
        visible = !isCollapsed,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(barBg)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Bên trái: Đèn trạng thái kết nối, Version & IP (Click để mở cài đặt kết nối)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isWorking) {
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
                    text = "v${com.example.agyremote.BuildConfig.VERSION_NAME}",
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "$hostIp:$port",
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace
                )
            }

            // Bên phải: Các nút công cụ thao tác
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // Nút Chọn ảnh từ thiết bị
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onOpenImagePicker() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "Chọn ảnh từ máy",
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF38BDF8)
                    )
                }

                // Nút Dán ảnh từ Clipboard
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onPasteClipboardImage() },
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
                            Icons.Default.ContentPaste,
                            contentDescription = "Dán ảnh từ Clipboard",
                            modifier = Modifier.size(14.dp),
                            tint = if (clipboardImageUri != null) Color(0xFF22C55E) else textColor.copy(alpha = 0.5f)
                        )
                    }
                }

                // Nút xem Log ADB
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onShowLogs() },
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
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Tải lại trang",
                        modifier = Modifier.size(14.dp),
                        tint = textColor.copy(alpha = 0.65f)
                    )
                }

                // Nút Mở rộng thẻ Action của Agent (< >)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onExpandActions() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = "Mở tất cả thẻ Action",
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF38BDF8)
                    )
                }

                // Nút Cài đặt
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onOpenSettings() },
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
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onToggleCollapse() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Thu gọn/Mở rộng",
                        modifier = Modifier.size(16.dp),
                        tint = textColor.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
