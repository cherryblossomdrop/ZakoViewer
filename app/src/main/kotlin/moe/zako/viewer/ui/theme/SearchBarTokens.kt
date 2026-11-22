package moe.zako.viewer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局搜索框 Design Token
 * 规范全应用搜索框的几何轮廓、图标规格、颜色约束与点击动效
 */
object SearchBarTokens {
    // 容器几何规格
    val ContainerShape: Shape = RoundedCornerShape(28.dp)
    val CornerRadius: Dp = 28.dp
    val IconSize: Dp = 24.dp
    val ActionButtonSize: Dp = 40.dp

    // 默认外边距
    val DefaultHorizontalPadding: Dp = 16.dp
    val DefaultVerticalPadding: Dp = 4.dp

    // 颜色 Token：中性前景色，禁止使用 primary 主题色
    val IconTint: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    val PlaceholderColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    // 触感微动效 Token
    const val ClearAnimDurationMs: Int = 180
    val ClearAnimEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}
