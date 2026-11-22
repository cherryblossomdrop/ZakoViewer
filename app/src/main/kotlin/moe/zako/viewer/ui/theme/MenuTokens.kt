package moe.zako.viewer.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 菜单样式 Design Token
 * 规范长按上下文菜单、底部操作栏（ActionSheet / ContextMenu）的视觉样式与尺寸参数
 * 底层对齐 Dimens 基础令牌
 */
object MenuTokens {
    // 菜单容器形状与圆角
    val ContainerShape: Shape = RoundedCornerShape(topStart = CornerRadius.Lg, topEnd = CornerRadius.Lg)
    val ItemShape: Shape = RoundedCornerShape(CornerRadius.Sm)

    // 阴影与色调标高
    val TonalElevation: Dp = Elevation.Level1
    val ShadowElevation: Dp = Elevation.Level4

    // 边距与间距
    val SheetPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = Spacing.Md)
    val SheetBottomPadding: Dp = 20.dp
    val TitleBottomSpacing: Dp = Spacing.Md
    val ItemSpacing: Dp = Spacing.Xs
    val IconTextSpacing: Dp = Spacing.Lg

    // 尺寸 Token
    val IconSize: Dp = moe.zako.viewer.ui.theme.IconSize.Lg
    val SupportingTextSize: TextUnit = 12.sp

    // 危险操作前景色（在 Composable 上下文中解析当前主题的 error 颜色）
    val DangerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.error

    val IconDefaultTint: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant
}
