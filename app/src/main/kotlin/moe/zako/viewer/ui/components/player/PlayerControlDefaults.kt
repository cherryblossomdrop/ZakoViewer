package moe.zako.viewer.ui.components.player

import androidx.compose.ui.unit.dp

/**
 * 视频播放控制层统一尺寸与容器规范常量
 * 统一所有播放器界面（独立播放页、详情页内嵌态及全屏态）的控制图标尺寸、点击容器与视觉对齐基准
 */
object PlayerControlDefaults {
    /** 常规控制图标标准大小 (24.dp) */
    val ControlIconSize = 24.dp

    /** 按钮可点击触控容器标准尺寸 (40.dp) */
    val ControlButtonSize = 40.dp

    /** 按钮之间横向标准间距 (8.dp) */
    val ControlSpacing = 8.dp

    /** 针对斜向/对角线异形图标 (如旋转屏幕) 的视觉等重补偿尺寸 (21.dp，保持 24.dp 居中外盒) */
    val CompensatedIconSize = 21.dp

    /** 锁屏图标标准尺寸 (24.dp) */
    val LockIconSize = 24.dp

    /** 锁屏圆形悬浮背景内边距 (10.dp，总外径 44.dp) */
    val LockContainerPadding = 10.dp
}
