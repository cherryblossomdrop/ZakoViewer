package moe.zako.viewer.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * 全局搜索框 Design Token
 * 规范全应用搜索框的几何轮廓、图标规格、颜色约束与点击动效
 * 底层对齐 Dimens 与 MotionTokens 基础令牌
 */
object SearchBarTokens {
    // 容器几何规格
    val ContainerShape: Shape = RoundedCornerShape(moe.zako.viewer.ui.theme.CornerRadius.Pill)
    val CornerRadius: Dp = moe.zako.viewer.ui.theme.CornerRadius.Pill
    val IconSize: Dp = moe.zako.viewer.ui.theme.IconSize.Lg
    val ActionButtonSize: Dp = TouchTarget.Compact

    // 默认外边距
    val DefaultHorizontalPadding: Dp = Spacing.Lg
    val DefaultVerticalPadding: Dp = Spacing.Xs

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
    const val ClearAnimDurationMs: Int = MotionTokens.Duration.Normal
    val ClearAnimEasing: Easing = MotionTokens.EasingTokens.Expressive
}
