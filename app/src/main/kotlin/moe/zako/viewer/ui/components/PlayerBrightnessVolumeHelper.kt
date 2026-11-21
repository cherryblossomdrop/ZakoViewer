package moe.zako.viewer.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

/**
 * 递归查找 Context 对应的真实 Activity
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object PlayerBrightnessHelper {
    /**
     * 读取设备当前真实的系统亮度（归一化为 0.01f..1.0f）
     * 1. 优先读取 screen_brightness_float (Android 9+ 常见，直接返回 0.0f..1.0f)
     * 2. 回退读取 Settings.System.SCREEN_BRIGHTNESS，并除以真实设备最大亮度 config_screenBrightnessSettingMaximum
     * 3. 兜底 0.5f
     */
    fun getSystemBrightness(context: Context): Float {
        val contentResolver = context.contentResolver
        // 1. 优先尝试读取系统浮点亮度 screen_brightness_float
        try {
            val floatVal = Settings.System.getFloat(contentResolver, "screen_brightness_float")
            if (floatVal in 0.0f..1.0f) {
                return floatVal.coerceIn(0.01f, 1.0f)
            }
        } catch (_: Throwable) {
            // 继续 fallback
        }

        // 2. 回退读取 Settings.System.SCREEN_BRIGHTNESS 整数并按设备真实最大亮度归一化
        try {
            val intVal = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val maxBrightness = getMaxBrightnessSetting()
            if (maxBrightness > 0f) {
                return (intVal / maxBrightness).coerceIn(0.01f, 1.0f)
            }
        } catch (_: Throwable) {
            // 继续 fallback
        }

        // 3. 安全兜底
        return 0.5f
    }

    private fun getMaxBrightnessSetting(): Float {
        return try {
            val resId = Resources.getSystem().getIdentifier(
                "config_screenBrightnessSettingMaximum",
                "integer",
                "android"
            )
            if (resId > 0) {
                val max = Resources.getSystem().getInteger(resId).toFloat()
                if (max > 0f) max else 255f
            } else {
                255f
            }
        } catch (_: Throwable) {
            255f
        }
    }

    /**
     * 每次拖拽起手（onDragStart）读取当前有效基准亮度：
     * - 优先读取当前 Activity 窗口属性 window.attributes.screenBrightness
     * - 若在 0f..1f 范围内，说明当前已被手动调节过，直接作为起点；
     * - 若 < 0f (即 BRIGHTNESS_OVERRIDE_NONE)，说明处于跟随系统亮度模式，读取系统真实亮度。
     */
    fun getCurrentBrightness(activity: Activity?): Float {
        val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        return if (windowBrightness in 0f..1f) {
            windowBrightness
        } else {
            activity?.let { getSystemBrightness(it) } ?: 0.5f
        }
    }

    /**
     * 设置当前窗口的独立亮度
     */
    fun setWindowBrightness(activity: Activity?, brightness: Float) {
        activity?.let { act ->
            val window = act.window ?: return@let
            val lp = window.attributes
            lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
            window.attributes = lp
        }
    }

    /**
     * 恢复窗口亮度跟随系统默认 (BRIGHTNESS_OVERRIDE_NONE = -1f)
     */
    fun restoreWindowBrightness(activity: Activity?) {
        activity?.let { act ->
            val window = act.window ?: return@let
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }
}

data class VolumeState(
    val currentVolume: Int,
    val minVolume: Int,
    val maxVolume: Int
) {
    val range: Int
        get() = (maxVolume - minVolume).coerceAtLeast(1)
}

object PlayerVolumeHelper {
    /**
     * 实时获取系统媒体音量状态
     */
    fun getVolumeState(audioManager: AudioManager?): VolumeState {
        if (audioManager == null) {
            return VolumeState(currentVolume = 0, minVolume = 0, maxVolume = 15)
        }
        val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } catch (_: Throwable) {
                0
            }
        } else {
            0
        }
        val maxVol = try {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        } catch (_: Throwable) {
            15
        }
        val curVol = try {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVol, maxVol)
        } catch (_: Throwable) {
            minVol
        }
        return VolumeState(
            currentVolume = curVol,
            minVolume = minVol,
            maxVolume = maxVol
        )
    }

    /**
     * 设置系统媒体音量
     */
    fun setStreamVolume(audioManager: AudioManager?, volume: Int) {
        if (audioManager == null) return
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        } catch (_: Throwable) {
            // Ignored
        }
    }
}
