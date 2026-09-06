package com.example.agyremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class QuickKey(
    val label: String,
    val textToInsert: String,
    val isAction: Boolean = false,
    val actionType: String = ""
)

/**
 * Thanh phím tắt lập trình nhanh (Accessory Coding Bar)
 * Giúp lập trình viên gõ nhanh ký tự lập trình trên điện thoại di động 1-chạm.
 */
@Composable
fun AccessoryCodingBar(
    onInsertText: (String) -> Unit,
    onInsertNewline: () -> Unit,
    onClearInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val barBg = Color(0xFF131722)
    val btnBg = Color(0xFF242A38)
    val textColor = Color(0xFFF8FAFC)
    val accentColor = Color(0xFF38BDF8)

    val keys = listOf(
        QuickKey("`", "`"),
        QuickKey("```", "```\n\n```"),
        QuickKey("/", "/"),
        QuickKey("@", "@"),
        QuickKey("()", "()"),
        QuickKey("{}", "{}"),
        QuickKey("[]", "[]"),
        QuickKey("\"\"", "\"\""),
        QuickKey(":", ":"),
        QuickKey("->", " -> "),
        QuickKey("=>", " => "),
        QuickKey("↵ Xuống dòng", "\n", isAction = true, actionType = "newline"),
        QuickKey("⌫ Xóa hết", "", isAction = true, actionType = "clear")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(barBg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(keys) { key ->
                val isAccent = key.isAction || key.label.startsWith("`") || key.label == "/"
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (key.actionType == "clear") Color(0xFFEF4444).copy(alpha = 0.25f) else btnBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            when (key.actionType) {
                                "newline" -> onInsertNewline()
                                "clear" -> onClearInput()
                                else -> onInsertText(key.textToInsert)
                            }
                        }
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (key.actionType == "clear") Color(0xFFF87171) else if (isAccent) accentColor else textColor
                    )
                }
            }
        }
    }
}
