package moe.zako.viewer.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局基础尺寸与间距设计令牌 (Design Tokens)
 * 采用清晰的语义化分级命名（禁止数值式 Size16 等命名），统一全应用的间距、圆角、图标大小与标高
 */

/**
 * 语义化间距令牌
 */
object Spacing {
    /** 极微间距 (2.dp) - 用于超紧凑微调或紧贴图标文字对齐 */
    val Xxs: Dp = 2.dp

    /** 紧凑小间距 (4.dp) - 用于同组紧密关联元素、角标内边距或标签微间距 */
    val Xs: Dp = 4.dp

    /** 标准小间距 (8.dp) - 用于常规控件间距、列表项内部元素间隔 */
    val Sm: Dp = 8.dp

    /** 标准中间距 (12.dp) - 用于卡片内边距、相邻内容区块间隔 */
    val Md: Dp = 12.dp

    /** 标准大间距 (16.dp) - 用于页面水平边距、列表外边距、区块分界 */
    val Lg: Dp = 16.dp

    /** 超大间距 (24.dp) - 用于弹窗外边距、底部操作区留白 */
    val Xl: Dp = 24.dp

    /** 特大间距 (32.dp) - 用于空状态居中外边距、大区块呼吸留白 */
    val Xxl: Dp = 32.dp
}

/**
 * 语义化圆角令牌
 */
object CornerRadius {
    /** 极小圆角 (4.dp) - 用于微型角标、小型状态标签 */
    val Xs: Dp = 4.dp

    /** 小圆角 (8.dp) - 用于菜单项、小型卡片、输入框局部圆角 */
    val Sm: Dp = 8.dp

    /** 中圆角 (12.dp) - 用于标准视频卡片、设置卡片 */
    val Md: Dp = 12.dp

    /** 大圆角 (16.dp) - 用于底部抽屉、标准对话框、历史搜索面板 */
    val Lg: Dp = 16.dp

    /** 特大圆角 (24.dp) - 用于大型卡片容器 */
    val Xl: Dp = 24.dp

    /** 胶囊圆角 (28.dp) - 用于全应用搜索框等胶囊形态组件 */
    val Pill: Dp = 28.dp

    /** 绝对圆形 (9999.dp) - 用于圆形头像、圆形悬浮按钮 */
    val Full: Dp = 9999.dp
}

/**
 * 语义化图标尺寸令牌
 */
object IconSize {
    /** 极微小图标 (14.dp) - 用于删除微按钮、状态角标内嵌图标 */
    val Xs: Dp = 14.dp

    /** 微小图标 (16.dp) - 用于行内标签、紧凑按钮、辅助说明图标 */
    val Sm: Dp = 16.dp

    /** 标准中图标 (20.dp) - 用于筛选栏按钮、紧凑导航栏图标 */
    val Md: Dp = 20.dp

    /** 标准大图标 (24.dp) - 用于顶栏导航、标准操作菜单、播放器常规控制 */
    val Lg: Dp = 24.dp

    /** 特大图标 (32.dp) - 用于播放器重要状态遮罩图标 */
    val Xl: Dp = 32.dp

    /** 展示型图标 (48.dp) - 用于空状态插画、空列表提示图标 */
    val Xxl: Dp = 48.dp
}

/**
 * 触控目标尺寸令牌（严格遵循 Material 3 可访问性标准）
 */
object TouchTarget {
    /** 标准无障碍触控目标最小尺寸 (48.dp) */
    val Min: Dp = 48.dp

    /** 紧凑型触控容器尺寸 (40.dp) */
    val Compact: Dp = 40.dp

    /** 密集型触控容器尺寸 (36.dp) - 用于筛选条圆形按钮等高密度控件 */
    val Dense: Dp = 36.dp
}

/**
 * 语义化边框宽度令牌
 */
object BorderWidth {
    /** 极细分割线/描边 (0.5.dp) */
    val Hairline: Dp = 0.5.dp

    /** 标准细边框 (1.dp) - 用于 Outlined 容器、未选中芯片描边 */
    val Thin: Dp = 1.dp

    /** 中度边框 (1.5.dp) - 用于微强调外边框 */
    val Medium: Dp = 1.5.dp

    /** 加粗焦点边框 (2.dp) - 用于激活/选中态高亮边框 */
    val Thick: Dp = 2.dp
}

/**
 * 语义化标高与阴影令牌
 */
object Elevation {
    /** 扁平无标高 (0.dp) */
    val Level0: Dp = 0.dp

    /** 极低标高 (1.dp) - 用于轻微卡片浮起、底部抽屉基准面 */
    val Level1: Dp = 1.dp

    /** 低标高 (3.dp) - 用于悬浮按钮、交互浮起卡片 */
    val Level2: Dp = 3.dp

    /** 中标高 (6.dp) - 用于悬浮 Toast、轻量级气泡 */
    val Level3: Dp = 6.dp

    /** 高标高 (8.dp) - 用于菜单抽屉、模态弹窗 */
    val Level4: Dp = 8.dp

    /** 最高标高 (12.dp) - 用于全屏控制覆盖物、顶层浮层 */
    val Level5: Dp = 12.dp
}

/**
 * 通用视频封面规格与比例令牌
 */
object VideoCoverTokens {
    /** 标准视频画幅宽高比 (16:9) */
    const val AspectRatio: Float = 16f / 9f

    /** 列表横向卡片标准封面宽度 (120.dp) */
    val ListCardWidth: Dp = 120.dp

    /** 列表横向卡片标准封面高度 (68.dp) */
    val ListCardHeight: Dp = 68.dp

    /** 瀑布流与网格封面最小推荐网格单元尺寸 (160.dp) */
    val GridAdaptiveMinSize: Dp = 160.dp
}
