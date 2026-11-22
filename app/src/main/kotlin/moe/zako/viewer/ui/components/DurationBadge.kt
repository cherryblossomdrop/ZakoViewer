package moe.zako.viewer.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 视频时长统一格式化中枢
 * 兼容浮点秒（如 "6515.63" -> "01:48:35"）、整数秒、毫秒及已格式化文本，过滤无效或 <= 0 时长
 */
object DurationFormatter {
    fun format(duration: String?): String {
        if (duration.isNullOrBlank()) return ""
        val trimmed = duration.trim()
        if (trimmed == "0" || trimmed == "00:00" || trimmed == "0:00" || trimmed == "00:00:00") return ""

        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            if (parts.all { it.toIntOrNull() != null }) {
                val totalSecs = when (parts.size) {
                    2 -> parts[0].toInt() * 60 + parts[1].toInt()
                    3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                    else -> 0
                }
                if (totalSecs <= 0) return ""
                return trimmed
            }
        }

        val doubleSecs = trimmed.toDoubleOrNull() ?: return ""
        val totalSecs = doubleSecs.toInt()
        if (totalSecs <= 0) return ""

        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatMs(durationMs: Long): String {
        if (durationMs <= 0L) return ""
        val totalSecs = (durationMs / 1000L).toInt()
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * 4 个列表页（离线下载/历史记录/收藏夹/稍后再看）与推荐页完全一致的封面右下角时长角标
 */
@Composable
fun BoxScope.DurationBadge(
    duration: String?,
    modifier: Modifier = Modifier
) {
    val formatted = remember(duration) { DurationFormatter.format(duration) }
    if (formatted.isNotEmpty()) {
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(4.dp),
            modifier = modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = formatted,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
            )
        }
    }
}
