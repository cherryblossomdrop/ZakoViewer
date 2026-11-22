package moe.zako.viewer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Expressive Accents
val VipGold = Color(0xFFFFB300)
val VipGoldBackground = Color(0x33FFB300)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E1E)
val PrimaryExpressive = Color(0xFF388E3C)
val OverlayBlack = Color(0xB3000000)

/**
 * 7 款系统预置主题色（严格保持两个汉字命名，内置预置不可删除）
 */
enum class ThemePreset(
    val key: String,
    val displayName: String,
    val hexColor: String,
    val primaryColor: Color,
    val isPreset: Boolean = true
) {
    PURPLE("#686BDD", "堇紫", "#686BDD", Color(0xFF686BDD)),
    LIGHT_GREEN("#5BB974", "浅绿", "#5BB974", Color(0xFF5BB974)),
    LIGHT_YELLOW("#E5C138", "淡黄", "#E5C138", Color(0xFFE5C138)),
    SAKURA_PINK("#F06292", "樱粉", "#F06292", Color(0xFFF06292)),
    SCALLION_GREEN("#2E9E5B", "葱绿", "#2E9E5B", Color(0xFF2E9E5B)),
    SKY_BLUE("#38BDF8", "天蓝", "#38BDF8", Color(0xFF38BDF8)),
    OCEAN_BLUE("#1D63B8", "海蓝", "#1D63B8", Color(0xFF1D63B8)),
    AZURE_BLUE("#008BF3", "蔚蓝", "#008BF3", Color(0xFF008BF3));

    companion object {
        fun fromKey(key: String): ThemePreset? =
            values().firstOrNull { it.key.equals(key.trim(), ignoreCase = true) || it.hexColor.equals(key.trim(), ignoreCase = true) }

        fun isBuiltinPreset(hex: String): Boolean =
            values().any { it.hexColor.equals(hex.trim(), ignoreCase = true) }
    }
}

// 1. 堇紫 (#686BDD)
val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC3C5FF),
    onPrimary = Color(0xFF26269A),
    primaryContainer = Color(0xFF3E40B1),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Color(0xFFC6C4DD),
    onSecondary = Color(0xFF2F2F42),
    secondaryContainer = Color(0xFF464559),
    onSecondaryContainer = Color(0xFFE2E0F9),
    tertiary = Color(0xFFEAB9CF),
    onTertiary = Color(0xFF472639),
    background = Color(0xFF131318),
    surface = Color(0xFF131318),
    surfaceVariant = Color(0xFF24232C),
    onSurface = Color(0xFFE5E1E6),
    onSurfaceVariant = Color(0xFFC8C5D0)
)

val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF5558C9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF0F0E64),
    secondary = Color(0xFF5D5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E0F9),
    onSecondaryContainer = Color(0xFF1A1A2C),
    tertiary = Color(0xFF7A5368),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF8FD),
    surface = Color(0xFFFBF8FD),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF47464F)
)

// 2. 浅绿 (#5BB974)
val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF89DA9C),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005327),
    onPrimaryContainer = Color(0xFFA5F7B7),
    secondary = Color(0xFFB8CCB8),
    onSecondary = Color(0xFF243426),
    secondaryContainer = Color(0xFF3A4B3B),
    onSecondaryContainer = Color(0xFFD4E8D3),
    tertiary = Color(0xFFA1CED9),
    onTertiary = Color(0xFF00363F),
    background = Color(0xFF111411),
    surface = Color(0xFF111411),
    surfaceVariant = Color(0xFF202620),
    onSurface = Color(0xFFE2E3DF),
    onSurfaceVariant = Color(0xFFC2C9BF)
)

val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF1B8242),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA5F7B7),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF516352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8D3),
    onSecondaryContainer = Color(0xFF0F1F12),
    tertiary = Color(0xFF39656E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7FCF5),
    surface = Color(0xFFF7FCF5),
    surfaceVariant = Color(0xFFDEE5DA),
    onSurface = Color(0xFF191C19),
    onSurfaceVariant = Color(0xFF424941)
)

// 3. 淡黄 (#E5C138)
val YellowDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE7C43C),
    onPrimary = Color(0xFF3B2F00),
    primaryContainer = Color(0xFF554500),
    onPrimaryContainer = Color(0xFFFFE084),
    secondary = Color(0xFFD2C6A2),
    onSecondary = Color(0xFF373016),
    secondaryContainer = Color(0xFF4E472A),
    onSecondaryContainer = Color(0xFFEFE2BD),
    tertiary = Color(0xFFA9D0B4),
    onTertiary = Color(0xFF143723),
    background = Color(0xFF15130E),
    surface = Color(0xFF15130E),
    surfaceVariant = Color(0xFF29261E),
    onSurface = Color(0xFFE8E2D8),
    onSurfaceVariant = Color(0xFFCDC6B5)
)

val YellowLightColorScheme = lightColorScheme(
    primary = Color(0xFF715C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE084),
    onPrimaryContainer = Color(0xFF231B00),
    secondary = Color(0xFF675E40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE2BD),
    onSecondaryContainer = Color(0xFF211B04),
    tertiary = Color(0xFF43664F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBF5),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFEAE2D0),
    onSurface = Color(0xFF1E1B16),
    onSurfaceVariant = Color(0xFF4B4639)
)

// 4. 樱粉 (#F06292)
val SakuraPinkDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB0C8),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE2BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5B3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFF1B7A2),
    onTertiary = Color(0xFF4A2818),
    background = Color(0xFF191113),
    surface = Color(0xFF191113),
    surfaceVariant = Color(0xFF2C2224),
    onSurface = Color(0xFFECE0E2),
    onSurfaceVariant = Color(0xFFD2C2C6)
)

val SakuraPinkLightColorScheme = lightColorScheme(
    primary = Color(0xFFD81B60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF815340),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F8),
    surface = Color(0xFFFFF8F8),
    surfaceVariant = Color(0xFFF2DDE1),
    onSurface = Color(0xFF201A1C),
    onSurfaceVariant = Color(0xFF514347)
)

// 5. 葱绿 (#2E9E5B)
val ScallionGreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AD5A3),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA5F2BE),
    secondary = Color(0xFFB7CCB9),
    onSecondary = Color(0xFF233527),
    secondaryContainer = Color(0xFF394B3C),
    onSecondaryContainer = Color(0xFFD3E8D4),
    tertiary = Color(0xFFA2CED8),
    onTertiary = Color(0xFF02363E),
    background = Color(0xFF101411),
    surface = Color(0xFF101411),
    surfaceVariant = Color(0xFF222722),
    onSurface = Color(0xFFE1E3DF),
    onSurfaceVariant = Color(0xFFC1C9C0)
)

val ScallionGreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E8246),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA5F2BE),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF506353),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D4),
    onSecondaryContainer = Color(0xFF0E1F13),
    tertiary = Color(0xFF3A656E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7FCF5),
    surface = Color(0xFFF7FCF5),
    surfaceVariant = Color(0xFFDEE5DC),
    onSurface = Color(0xFF181C19),
    onSurfaceVariant = Color(0xFF424942)
)

// 6. 天蓝 (#38BDF8)
val SkyBlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF00354F),
    primaryContainer = Color(0xFF004D70),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF20333E),
    secondaryContainer = Color(0xFF384955),
    onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFFCCC1E9),
    onTertiary = Color(0xFF332B4C),
    background = Color(0xFF0F1418),
    surface = Color(0xFF0F1418),
    surfaceVariant = Color(0xFF22272C),
    onSurface = Color(0xFFDFE3E7),
    onSurfaceVariant = Color(0xFFC1C8CF)
)

val SkyBlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF4F616E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF63597C),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFFDEE3EA),
    onSurface = Color(0xFF171C20),
    onSurfaceVariant = Color(0xFF42474E)
)

// 7. 海蓝 (#1D63B8)
val OceanBlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF004689),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDFBCDF),
    onTertiary = Color(0xFF412743),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF24252C),
    onSurface = Color(0xFFE3E2E9),
    onSurfaceVariant = Color(0xFFC4C6D0)
)

val OceanBlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF1D63B8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF131B2C),
    tertiary = Color(0xFF705574),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF8FD),
    surface = Color(0xFFFBF8FD),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurface = Color(0xFF1A1B21),
    onSurfaceVariant = Color(0xFF45464F)
)

// 8. 蔚蓝 (#008BF3)
val AzureBlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF82CFFF),
    onPrimary = Color(0xFF00345B),
    primaryContainer = Color(0xFF004B80),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F7),
    tertiary = Color(0xFFD2BFE7),
    onTertiary = Color(0xFF382A4A),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF22262B),
    onSurface = Color(0xFFE0E2E8),
    onSurfaceVariant = Color(0xFFC2C7CF)
)

val AzureBlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF008BF3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E37),
    secondary = Color(0xFF51606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF67587A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FD),
    surface = Color(0xFFF7F9FD),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurface = Color(0xFF181C20),
    onSurfaceVariant = Color(0xFF42474E)
)

/**
 * 解析 HEX 颜色代码（支持 #RRGGBB 或 RRGGBB）
 */
fun parseHexColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    return try {
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (_: Exception) {
        null
    }
}

/**
 * 基于任意种子颜色生成完整的 Material 3 明暗配色方案
 */
fun generateColorScheme(seed: Color, darkTheme: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (seed.red * 255).toInt(),
        (seed.green * 255).toInt(),
        (seed.blue * 255).toInt(),
        hsv
    )
    val hue = hsv[0]
    val sat = hsv[1]

    fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf((h % 360f + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        return Color(rgb)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = hsvToColor(hue, (sat * 0.7f).coerceAtLeast(0.2f), 0.90f),
            onPrimary = hsvToColor(hue, sat, 0.20f),
            primaryContainer = hsvToColor(hue, sat, 0.40f),
            onPrimaryContainer = hsvToColor(hue, (sat * 0.4f), 0.95f),
            secondary = hsvToColor((hue + 15f) % 360f, sat * 0.4f, 0.80f),
            onSecondary = hsvToColor(hue, sat * 0.5f, 0.20f),
            secondaryContainer = hsvToColor((hue + 15f) % 360f, sat * 0.4f, 0.35f),
            onSecondaryContainer = hsvToColor(hue, sat * 0.2f, 0.95f),
            tertiary = hsvToColor((hue + 60f) % 360f, sat * 0.5f, 0.85f),
            onTertiary = hsvToColor((hue + 60f) % 360f, sat, 0.20f),
            background = Color(0xFF141218),
            surface = Color(0xFF141218),
            surfaceVariant = Color(0xFF26242B),
            onBackground = Color(0xFFE6E1E5),
            onSurface = Color(0xFFE6E1E5),
            onSurfaceVariant = Color(0xFFCAC4D0)
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = hsvToColor(hue, sat * 0.25f, 0.95f),
            onPrimaryContainer = hsvToColor(hue, sat, 0.25f),
            secondary = hsvToColor((hue + 15f) % 360f, sat * 0.4f, 0.50f),
            onSecondary = Color.White,
            secondaryContainer = hsvToColor((hue + 15f) % 360f, sat * 0.2f, 0.92f),
            onSecondaryContainer = hsvToColor(hue, sat * 0.6f, 0.20f),
            tertiary = hsvToColor((hue + 60f) % 360f, sat * 0.5f, 0.50f),
            onTertiary = Color.White,
            background = Color(0xFFFAF9FD),
            surface = Color(0xFFFAF9FD),
            surfaceVariant = Color(0xFFE5E1EA),
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F),
            onSurfaceVariant = Color(0xFF49454E)
        )
    }
}

/**
 * 根据主题键值（预置枚举或 HEX 码）获取对应的明暗配色方案
 */
fun getColorSchemeForTheme(themeKey: String, darkTheme: Boolean): ColorScheme {
    val preset = ThemePreset.fromKey(themeKey)
    if (preset != null) {
        return when (preset) {
            ThemePreset.PURPLE -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
            ThemePreset.LIGHT_GREEN -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
            ThemePreset.LIGHT_YELLOW -> if (darkTheme) YellowDarkColorScheme else YellowLightColorScheme
            ThemePreset.SAKURA_PINK -> if (darkTheme) SakuraPinkDarkColorScheme else SakuraPinkLightColorScheme
            ThemePreset.SCALLION_GREEN -> if (darkTheme) ScallionGreenDarkColorScheme else ScallionGreenLightColorScheme
            ThemePreset.SKY_BLUE -> if (darkTheme) SkyBlueDarkColorScheme else SkyBlueLightColorScheme
            ThemePreset.OCEAN_BLUE -> if (darkTheme) OceanBlueDarkColorScheme else OceanBlueLightColorScheme
            ThemePreset.AZURE_BLUE -> if (darkTheme) AzureBlueDarkColorScheme else AzureBlueLightColorScheme
        }
    }

    val parsedColor = parseHexColor(themeKey)
    return if (parsedColor != null) {
        generateColorScheme(parsedColor, darkTheme)
    } else {
        if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
    }
}

/**
 * 视频播放器与全屏控制层语义色彩令牌
 */
object PlayerOverlayTokens {
    /** 播放器背景纯黑 */
    val Background: Color = Color(0xFF000000)

    /** 播放器控制层标准半透明遮罩背景 (40% 黑色) */
    val Scrim: Color = Color(0x66000000)

    /** 播放器控制层深色半透明遮罩背景 (50% 黑色) */
    val ScrimDark: Color = Color(0x80000000)

    /** HUD 手势浮层半透明背景 (70% 黑色) */
    val HudBackground: Color = Color(0xB3000000)

    /** VIP 金色徽章主要文字与边框色彩 */
    val VipGold: Color = Color(0xFFFFB300)

    /** VIP 金色徽章半透明背景容器色彩 (20% 金色) */
    val VipGoldContainer: Color = Color(0x33FFB300)

    /** VIP 抢先看徽章渐变起始色 */
    val VipBadgeStart: Color = Color(0xFFFFD54F)

    /** VIP 抢先看徽章渐变结束色 */
    val VipBadgeEnd: Color = Color(0xFFFF8F00)

    /** VIP 抢先看徽章内部前景色 */
    val VipBadgeContent: Color = Color(0xFF2E1C00)
}
