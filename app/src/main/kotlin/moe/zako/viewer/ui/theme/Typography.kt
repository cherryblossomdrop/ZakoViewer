package moe.zako.viewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 全局排版文字设计令牌 (Typography Tokens)
 * 集中规范全应用的标题、正文、标签、角标及代码字号与行高体系
 */
val ZakoTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 业务专用文字样式扩展令牌
 */
object ExtraTextStyles {
    /** 视频封面时长角标样式 (11.sp, 加粗, 等宽数字) */
    val DurationBadge: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace
    )

    /** 紧凑型芯片标签文字样式 (12.sp) */
    val ChipLabel: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )

    /** 诊断日志等宽文字样式 (11.sp) */
    val MonospaceLog: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace
    )
}

/**
 * 语义化字号令牌 (Font Sizes)
 */
object FontSizes {
    /** 超紧凑微型角标字号 (10.sp) */
    val MicroBadge: TextUnit = 10.sp

    /** 倍速徽标长字符极小字号 (9.sp) */
    val SpeedBadgeSmall: TextUnit = 9.sp

    /** 倍速徽标标准字号 (10.5.sp) */
    val SpeedBadge: TextUnit = 10.5.sp

    /** 极小角标字号 (11.sp) */
    val Badge: TextUnit = 11.sp

    /** 紧凑辅助说明字号 (12.sp) */
    val Caption: TextUnit = 12.sp

    /** 标签与轻量强调字号 (13.sp) */
    val Label: TextUnit = 13.sp

    /** 标准小正文字号 (14.sp) */
    val BodySmall: TextUnit = 14.sp

    /** 适中正文字号 (15.sp) */
    val BodyMedium: TextUnit = 15.sp

    /** 标准主正文字号 (16.sp) */
    val Body: TextUnit = 16.sp

    /** 次级标题字号 (18.sp) */
    val Subtitle: TextUnit = 18.sp

    /** 标准中标题字号 (20.sp) */
    val TitleMedium: TextUnit = 20.sp

    /** 强调大标题字号 (22.sp) */
    val TitleLarge: TextUnit = 22.sp

    /** 主视觉展示大标题字号 (24.sp) */
    val Headline: TextUnit = 24.sp
}
