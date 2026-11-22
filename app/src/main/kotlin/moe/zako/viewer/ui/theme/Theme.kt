package moe.zako.viewer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 全局隐私模糊保护开关 CompositionLocal，默认值必须为 true
 */
val LocalCoverBlurEnabled = compositionLocalOf { true }

@Composable
fun ZakoViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // 莫奈动态取色开关
    themePreset: ThemePreset = ThemePreset.PURPLE,
    themeKey: String = themePreset.hexColor,
    coverBlurEnabled: Boolean = true, // 视频封面默认模糊 (默认开启)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorSchemeForTheme(themeKey, darkTheme)
    }

    CompositionLocalProvider(LocalCoverBlurEnabled provides coverBlurEnabled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZakoTypography,
            content = content
        )
    }
}

@Composable
fun NeoViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themePreset: ThemePreset = ThemePreset.PURPLE,
    themeKey: String = themePreset.hexColor,
    coverBlurEnabled: Boolean = true,
    content: @Composable () -> Unit
) = ZakoViewerTheme(darkTheme, dynamicColor, themePreset, themeKey, coverBlurEnabled, content)
