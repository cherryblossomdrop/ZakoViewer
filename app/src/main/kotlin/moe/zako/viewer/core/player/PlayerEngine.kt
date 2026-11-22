package moe.zako.viewer.core.player

import android.view.Surface
import android.view.SurfaceHolder

enum class AspectRatioMode(val label: String) {
    FIT("适应屏幕"),
    FILL("全屏填充"),
    FIXED_16_9("16:9"),
    FIXED_4_3("4:3")
}

enum class PlayerEngine(val label: String) {
    SYSTEM("系统内核 (MediaPlayer)"),
    MPV("MPV 内核"),
    AUTO("自动")
}

enum class HwDecMode(val code: String, val label: String) {
    MEDIACODEC("mediacodec", "硬解 (mediacodec)"),
    MEDIACODEC_COPY("mediacodec-copy", "兼容硬解 (mediacodec-copy)"),
    AUTO("auto", "自动"),
    OFF("no", "软解 (关闭硬解)")
}

data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isBuffering: Boolean = false,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val isLocked: Boolean = false,
    val isSpeedBoosted: Boolean = false,
    val currentSpeed: Float = 1.0f,
    val normalSpeed: Float = 1.0f,
    val boostSpeed: Float = 2.0f,
    val title: String = "",
    val activeUrl: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val engine: PlayerEngine = PlayerEngine.MPV,
    val hwDecMode: HwDecMode = HwDecMode.MEDIACODEC_COPY
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val bufferedProgress: Float
        get() {
            if (durationMs <= 0L) return 0f
            val effectiveBuffered = maxOf(bufferedPositionMs, currentPositionMs)
            return (effectiveBuffered.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }

    val positionFormatted: String
        get() = formatTime(currentPositionMs)

    val durationFormatted: String
        get() = formatTime(durationMs)

    companion object {
        fun formatTime(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

/**
 * 内部播放引擎适配器统一接口 (PlayerEngineAdapter)
 * 适配器模式核心抽象，统一 MPV 原生内核与 Android 系统 MediaPlayer 的异构实现细节
 */
internal interface PlayerEngineAdapter {
    /** 标识当前引擎类型 (MPV / SYSTEM) */
    val engineType: PlayerEngine

    /** 当前引擎是否已就绪（可进行渲染与精准 Seek） */
    val isReady: Boolean

    /** 关联渲染表面 */
    fun attachSurface(surface: Surface, holder: SurfaceHolder? = null)

    /** 解绑渲染表面（幂等断开底层连接并释放引用） */
    fun detachSurface()

    /** 更新视口渲染尺寸（严格过滤 <= 0 的无效尺寸） */
    fun setSurfaceSize(width: Int, height: Int)

    /** 启动加载媒体源 */
    fun loadVideo(url: String, headers: Map<String, String>, resumePos: Long)

    /** 控制指令 */
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setAspectRatio(mode: AspectRatioMode)
    fun setHwDecMode(mode: HwDecMode)

    /** 幂等停止播放并重置媒体状态 */
    fun stop()

    /** 彻底释放引擎持有的原生/系统句柄（释放后不可再复用） */
    fun release()
}

/**
 * 引擎状态变更与事件回调契约
 */
internal interface EngineCallback {
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun onBufferingStateChanged(isBuffering: Boolean)
    fun onPositionChanged(positionMs: Long, durationMs: Long)
    fun onBufferedPositionChanged(bufferedMs: Long)
    fun onVideoSizeChanged(width: Int, height: Int)
    fun onPlaybackCompleted()
    fun onError(message: String, isFatal: Boolean = false)
}
