package moe.zako.viewer.ui.theme

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

enum class ThemePreset(
    val key: String,
    val displayName: String,
    val hexColor: String,
    val primaryColor: Color
) {
    PURPLE("#686BDD", "紫色", "#686BDD", Color(0xFF686BDD)),
    LIGHT_GREEN("#5BB974", "浅绿色", "#5BB974", Color(0xFF5BB974)),
    LIGHT_YELLOW("#E5C138", "淡黄色", "#E5C138", Color(0xFFE5C138));

    companion object {
        fun fromKey(key: String): ThemePreset =
            values().firstOrNull { it.key.equals(key, ignoreCase = true) || it.hexColor.equals(key, ignoreCase = true) } ?: PURPLE
    }
}

// 1. 紫色 (#686BDD)
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

// 2. 浅绿色 (#5BB974)
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

// 3. 淡黄色 (#E5C138)
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
