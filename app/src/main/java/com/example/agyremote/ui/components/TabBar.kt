package com.example.agyremote.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agyremote.ui.BrowserTab

/**
 * Thanh quản lý Tab (Statusbar 2).
 * Độc lập hoàn toàn với WebView và Network logic.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabBar(
    tabs: List<BrowserTab>,
    activeTabId: String,
    isFullscreen: Boolean,
    onSelectTab: (BrowserTab) -> Unit,
    onCloseTab: (String) -> Unit,
    onAddTab: () -> Unit,
    onToggleSidebar: () -> Unit,
    onRestoreFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val tabRowBgColor = Color(0xFF1E222D)
    val tabActiveBgColor = Color(0xFF2D3748)
    val tabInactiveBgColor = Color(0xFF181B22)
    val textColor = Color(0xFFF8FAFC)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
            .height(44.dp)
            .background(tabRowBgColor)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Nút Menu Toggle Dự án/phiên - Phiên đang mở
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable { onToggleSidebar() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Chuyển đổi Dự án / Phiên đang mở",
                modifier = Modifier.size(22.dp),
                tint = textColor.copy(alpha = 0.9f)
            )
        }

        // Nút khôi phục Statusbar 1 nếu đang ở chế độ toàn màn hình
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onRestoreFullscreen() },
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

        // 2. Danh sách các Tab
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
                            .combinedClickable(
                                onClick = { onSelectTab(tab) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(context, "📑 ${tab.title}", Toast.LENGTH_SHORT).show()
                                }
                            )
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
                                } else if (tab.hasUnread && !isActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
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
                                    text = if (tab.isWorking) "${tab.title} ⏳" else if (tab.hasUnread && !isActive) "🔴 ${tab.title}" else tab.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isActive || (tab.hasUnread && !isActive)) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isActive) textColor else if (tab.hasUnread && !isActive) Color(0xFFF87171) else textColor.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (tabs.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable { onCloseTab(tab.id) },
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

                // Nút thêm tab mới (+)
                item {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onAddTab() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Thêm tab mới",
                            modifier = Modifier.size(20.dp),
                            tint = textColor.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
