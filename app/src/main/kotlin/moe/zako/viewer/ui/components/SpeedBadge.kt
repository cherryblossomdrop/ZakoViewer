package moe.zako.viewer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun SpeedBadge(
    speed: Float,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val text = when {
        speed == 1.0f -> "1.0"
        speed == 1.5f -> "1.5"
        speed == 2.0f -> "2.0"
        speed % 1f == 0f -> String.format(Locale.US, "%.1f", speed)
        else -> {
            val formatted = String.format(Locale.US, "%.2f", speed)
            if (formatted.endsWith("0")) formatted.dropLast(1) else formatted
        }
    }

    Box(
        modifier = modifier
            .height(24.dp)
            .widthIn(min = 24.dp)
            .border(
                width = 1.8.dp,
                color = tint,
                shape = RoundedCornerShape(5.dp)
            )
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = if (text.length > 3) 9.sp else 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp,
            lineHeight = 12.sp
        )
    }
}
