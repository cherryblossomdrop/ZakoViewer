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
    /** 区块间全局标准间距 (16.dp) - 用于不同功能区块之间的分隔留白 */
    val SectionSpacing: Dp = 16.dp

    /** 同区块内同级子项间窄间距 (3.dp) - 遵循 MD3E 分组卡片与分段圆角规范 */
    val GroupItemSpacing: Dp = 3.dp

    /** 细微内边距 (1.5.dp) - 用于时长角标紧凑垂直内边距 */
    val BadgeVertical: Dp = 1.5.dp

    /** 极微间距 (2.dp) - 用于超紧凑微调或紧贴图标文字对齐 */
    val Xxs: Dp = 2.dp

    /** 倍速徽标水平微间距 (3.dp) */
    val SpeedBadge: Dp = 3.dp

    /** 紧凑条目垂直微间距 (3.dp) - 兼容历史命名，建议统一使用 GroupItemSpacing */
    val MicroItem: Dp = GroupItemSpacing

    /** 紧凑小间距 (4.dp) - 用于同组紧密关联元素、角标内边距或标签微间距 */
    val Xs: Dp = 4.dp

    /** 紧凑微间距 (6.dp) - 用于微型图标与文字横向间距 */
    val Compact: Dp = 6.dp

    /** 标准小间距 (8.dp) - 用于常规控件间距、列表项内部元素间隔 */
    val Sm: Dp = 8.dp

    /** 过渡间距 (10.dp) - 用于紧凑卡片垂直留白或内边距微调 */
    val Intermediate: Dp = 10.dp

    /** 标准中间距 (12.dp) - 用于卡片内边距、相邻内容区块间隔 */
    val Md: Dp = 12.dp

    /** 紧凑列表项纵向留白 (13.dp) */
    val ListItemVertical: Dp = 13.dp

    /** 扩展中间距 (14.dp) - 用于 HUD 与标签块垂直内边距 */
    val Expanded: Dp = 14.dp

    /** 标准大间距 (16.dp) - 用于页面水平边距、列表外边距、区块分界 */
    val Lg: Dp = 16.dp

    /** 深度大间距 (20.dp) - 用于内容段落与浮层间隔 */
    val Dp20: Dp = 20.dp

    /** 超大间距 (24.dp) - 用于弹窗外边距、底部操作区留白 */
    val Xl: Dp = 24.dp

    /** 极大间距 (28.dp) - 用于全屏控制层顶部留白 */
    val XxlCompact: Dp = 28.dp

    /** 特大间距 (32.dp) - 用于空状态居中外边距、大区块呼吸留白 */
    val Xxl: Dp = 32.dp

    /** 巨大间距 (48.dp) - 用于全屏横向大幅留白 */
    val Massive: Dp = 48.dp
}

/**
 * 语义化圆角令牌
 */
object CornerRadius {
    /** 极小圆角 (4.dp) - 用于微型角标、小型状态标签 */
    val Xs: Dp = 4.dp

    /** 倍速徽标专用圆角 (5.dp) */
    val SpeedBadge: Dp = 5.dp

    /** 紧凑圆角 (6.dp) - 用于徽章与紧凑标签 */
    val Compact: Dp = 6.dp

    /** 小圆角 (8.dp) - 用于菜单项、小型卡片、输入框局部圆角 */
    val Sm: Dp = 8.dp

    /** 过渡圆角 (10.dp) - 用于调色盘画板等中紧凑容器 */
    val Intermediate: Dp = 10.dp

    /** 中圆角 (12.dp) - 用于标准视频卡片、设置卡片 */
    val Md: Dp = 12.dp

    /** 分组卡片适度圆角 (14.dp) */
    val CardGroup: Dp = 14.dp

    /** 大圆角 (16.dp) - 用于底部抽屉、标准对话框、历史搜索面板 */
    val Lg: Dp = 16.dp

    /** 适中大圆角 (20.dp) - 用于提示卡片与浮动 HUD */
    val MediumLarge: Dp = 20.dp

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
    /** 极小微图标 (12.dp) - 用于标签内嵌微星星等 */
    val ExtraSmall: Dp = 12.dp

    /** 极微小图标 (14.dp) - 用于删除微按钮、状态角标内嵌图标 */
    val Xs: Dp = 14.dp

    /** 紧凑指示箭头图标 (15.dp) - 用于个人中心及设置列表项右侧 Chevron */
    val Chevron: Dp = 15.dp

    /** 微小图标 (16.dp) - 用于行内标签、紧凑按钮、辅助说明图标 */
    val Sm: Dp = 16.dp

    /** 次微小图标 (18.dp) - 用于倍速提示与输入框次级图标 */
    val Intermediate: Dp = 18.dp

    /** 标准中图标 (20.dp) - 用于筛选栏按钮、紧凑导航栏图标 */
    val Md: Dp = 20.dp

    /** 视觉等重补偿图标尺寸 (21.dp) - 用于旋转屏幕等斜向异形图标居中补偿 */
    val Compensated: Dp = 21.dp

    /** 适中偏大图标 (22.dp) - 用于下载进度环与居中状态指示 */
    val MediumLarge: Dp = 22.dp

    /** 标准大图标 (24.dp) - 用于顶栏导航、标准操作菜单、播放器常规控制 */
    val Lg: Dp = 24.dp

    /** 核心操作大图标 (26.dp) - 用于个人中心主卡片操作图标 */
    val LargeAction: Dp = 26.dp

    /** 主题取色与圆圈指示图标 (28.dp) */
    val ColorCircle: Dp = 28.dp

    /** 特大图标 (32.dp) - 用于播放器重要状态遮罩图标 */
    val Xl: Dp = 32.dp

    /** 巨大图标 (40.dp) - 用于播放器中央手势图标 */
    val Huge: Dp = 40.dp

    /** 展示型图标 (48.dp) - 用于空状态插画、空列表提示图标 */
    val Xxl: Dp = 48.dp

    /** 超大视效图标 (72.dp) - 用于播放器中央圆形播放指示容器 */
    val Massive: Dp = 72.dp
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

    /** 极小触控容器尺寸 (32.dp) */
    val ExtraCompact: Dp = 32.dp
}

/**
 * 通用弹窗与列表视口容器规格
 */
object DialogTokens {
    /** 弹窗内部滚动列表最大高度限制 (350.dp) */
    val MaxListHeight: Dp = 350.dp

    /** 紧凑型弹窗内部滚动列表高度限制 (300.dp) */
    val CompactListMaxHeight: Dp = 300.dp

    /** 目录浏览器列表最大高度限制 (320.dp) */
    val FolderBrowserMaxHeight: Dp = 320.dp

    /** 应用选择器列表最大高度限制 (340.dp) */
    val AppPickerMaxHeight: Dp = 340.dp

    /** 弹窗加载占位高度 (200.dp) */
    val LoadingBoxHeight: Dp = 200.dp

    /** 颜色预览卡片标准高度 (64.dp) */
    val ColorPreviewCardHeight: Dp = 64.dp

    /** 调色盘二维画布高度 (160.dp) */
    val ColorCanvasHeight: Dp = 160.dp
}

/**
 * 全局浮动提示组件设计令牌 (Toast Tokens)
 */
object ToastTokens {
    /** 胶囊浮层最小宽度 (96.dp) */
    val MinWidth: Dp = 96.dp

    /** 胶囊浮层最大宽度 (340.dp) */
    val MaxWidth: Dp = 340.dp

    /** 胶囊横向呼吸内边距 (22.dp) */
    val HorizontalPadding: Dp = 22.dp

    /** 胶囊纵向呼吸内边距 (11.dp) */
    val VerticalPadding: Dp = 11.dp
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

    /** 倍速徽标专用边框 (1.8.dp) */
    val SpeedBadge: Dp = 1.8.dp

    /** 加粗焦点边框 (2.dp) - 用于激活/选中态高亮边框 */
    val Thick: Dp = 2.dp

    /** 拾色器游标外圈描边 (2.5.dp) */
    val ColorPickerStroke: Dp = 2.5.dp

    /** 进度环标准描边 (2.5.dp) */
    val ProgressIndicator: Dp = 2.5.dp

    /** 粗轮廓/进度描边 (3.dp) */
    val HeavyStroke: Dp = 3.dp

    /** 拾色器游标内圆半径 (7.5.dp) */
    val ColorPickerInnerRadius: Dp = 7.5.dp
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

    /** 封面高斯脱敏模糊默认半径 (18.dp) */
    val BlurRadius: Dp = 18.dp

    /** 封面底部半透明遮罩标准高度 (48.dp) */
    val ScrimHeight: Dp = 48.dp
}

/**
 * 个人中心专属几何设计令牌
 */
object ProfileTokens {
    /** 个人中心顶部核心业务卡片高度 (112.dp) */
    val HeroCardHeight: Dp = 112.dp
}

/**
 * 进度指示器几何令牌
 */
object ProgressBarTokens {
    /** 线性进度条标准高度 (6.dp) */
    val Height: Dp = 6.dp
}

/**
 * 设置视窗专属设计令牌
 */
object SettingsTokens {
    /** 分类图标外层容器尺寸 (38.dp) */
    val CategoryIconContainerSize: Dp = 38.dp
}
