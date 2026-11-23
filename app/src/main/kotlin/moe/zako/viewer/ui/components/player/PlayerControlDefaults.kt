package moe.zako.viewer.ui.components.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget

/**
 * 视频播放控制层统一尺寸与容器规范常量
 * 统一所有播放器界面（独立播放页、详情页内嵌态及全屏态）的控制图标尺寸、点击容器与视觉对齐基准
 * 底层对齐 Dimens 基础令牌
 */
object PlayerControlDefaults {
    /** 控制条与锁定图标自动隐藏倒计时 (4000ms) */
    const val AutoHideTimeoutMs: Long = 4000L

    /** 常规控制图标标准大小 (24.dp) */
    val ControlIconSize: Dp = IconSize.Lg

    /** 按钮可点击触控容器标准尺寸 (40.dp) */
    val ControlButtonSize: Dp = TouchTarget.Compact

    /** 按钮之间横向标准间距 (8.dp) */
    val ControlSpacing: Dp = Spacing.Sm

    /** 针对斜向/对角线异形图标 (如旋转屏幕) 的视觉等重补偿尺寸 (21.dp，保持 24.dp 居中外盒) */
    val CompensatedIconSize: Dp = IconSize.Compensated

    /** 锁屏图标标准尺寸 (24.dp) */
    val LockIconSize: Dp = IconSize.Lg

    /** 锁屏圆形悬浮背景内边距 (10.dp，总外径 44.dp) */
    val LockContainerPadding: Dp = Spacing.Intermediate

    /** 内嵌播放器中央播放按钮容器尺寸 (64.dp) */
    val InlinePlayButtonSize: Dp = 64.dp

    /** 内嵌播放器中央播放图标尺寸 (40.dp) */
    val InlinePlayIconSize: Dp = IconSize.Huge
}
