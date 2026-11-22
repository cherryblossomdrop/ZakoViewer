package moe.zako.viewer.ui.theme

import androidx.compose.ui.unit.Dp

/**
 * 筛选栏操作按钮 Design Token
 * 集中规范首页筛选、排序、布局切换等圆形操作按钮的尺寸、图标及间距
 * 底层对齐 Dimens 基础令牌
 */
object FilterBarTokens {
    val ButtonSize: Dp = TouchTarget.Dense
    val IconSize: Dp = moe.zako.viewer.ui.theme.IconSize.Md
    val ItemSpacing: Dp = Spacing.Sm
}
