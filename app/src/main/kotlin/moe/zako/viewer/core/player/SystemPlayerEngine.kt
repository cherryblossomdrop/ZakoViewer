package moe.zako.viewer.core.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.view.Surface
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.zako.viewer.core.logger.ZakoLog
import java.io.File
import java.util.concurrent.Executors

/**
 * Android 系统 MediaPlayer 播放引擎适配器实现 (SystemPlayerEngine)
 * 将全部跨进程 MediaPlayer IPC 调用严格收敛在后台独立单线程队列中串行派发，
 * 彻底消除主线程阻塞，保障 Surface 解绑与内核切换的并发与生命周期安全。
 */
internal class SystemPlayerEngine(
    private val context: Context,
    private val callback: EngineCallback
) : PlayerEngineAdapter {

    companion object {
        private const val TAG = "SystemPlayerEngine"
    }

    override val engineType: PlayerEngine = PlayerEngine.SYSTEM

    @Volatile
    private var isPrepared = false

    override val isReady: Boolean
        get() = isPrepared

    private val surfaceLock = Any()
    private var attachedSurface: Surface? = null
    private var attachedHolder: SurfaceHolder? = null

    private var mediaPlayer: MediaPlayer? = null
    private var pendingResumePositionMs = 0L
    private var currentSpeed: Float = 1.0f

    @Volatile
    private var inFlightSeekTargetMs = -1L
    @Volatile
    private var inFlightSeekTimestamp = 0L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTickerJob: Job? = null

    // 独立单线程调度器，专门串行派发全部 MediaPlayer IPC 与 Surface 绑定/解绑操作，严禁在 UI 主线程执行阻塞操作
    private val mediaPlayerExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ZakoPlayer-SystemMediaPlayer-IPC").apply { isDaemon = true }
    }
    private val mediaPlayerDispatcher = mediaPlayerExecutor.asCoroutineDispatcher()
    private val mediaPlayerScope = CoroutineScope(mediaPlayerDispatcher + SupervisorJob())

    private fun postMediaPlayerOp(action: (MediaPlayer) -> Unit) {
        val player = mediaPlayer ?: return
        mediaPlayerScope.launch {
            try {
                action(player)
            } catch (e: Throwable) {
                ZakoLog.w(TAG, "Async MediaPlayer operation failed", e)
            }
        }
    }

    override fun attachSurface(surface: Surface, holder: SurfaceHolder?) {
        synchronized(surfaceLock) {
            attachedSurface = surface
            attachedHolder = holder
        }
        postMediaPlayerOp { mp ->
            try {
                synchronized(surfaceLock) {
                    if (holder != null && holder.surface.isValid) {
                        mp.setDisplay(holder)
                        ZakoLog.d(TAG, "attachSurface: MediaPlayer display bound successfully")
                    } else if (surface.isValid) {
                        mp.setSurface(surface)
                        ZakoLog.d(TAG, "attachSurface: MediaPlayer surface bound successfully")
                    }
                }
            } catch (e: Throwable) {
                ZakoLog.w(TAG, "attachSurface MediaPlayer error", e)
            }
        }
    }

    override fun detachSurface() {
        synchronized(surfaceLock) {
            attachedSurface = null
            attachedHolder = null
            // 关键：在主线程即刻断开 Surface 绑定引用，释放底层 BufferQueue 占用，杜绝跨引擎切换时引发 EGL 冲突
            mediaPlayer?.let { mp ->
                try { mp.setDisplay(null) } catch (_: Throwable) {}
                try { mp.setSurface(null) } catch (_: Throwable) {}
            }
        }
        // 严格串行安全：在 mediaPlayerScope 队列中安全解绑，彻底避免主线程与后台线程并发操作导致的竞态
        postMediaPlayerOp { mp ->
            try {
                if (mp.isPlaying) {
                    try { mp.pause() } catch (_: Throwable) {}
                }
                try { mp.setDisplay(null) } catch (_: Throwable) {}
                try { mp.setSurface(null) } catch (_: Throwable) {}
                ZakoLog.d(TAG, "detachSurface: MediaPlayer display safely unbound in background queue")
            } catch (e: Throwable) {
                ZakoLog.w(TAG, "detachSurface MediaPlayer error", e)
            }
        }
    }

    override fun setSurfaceSize(width: Int, height: Int) {
        // 系统 MediaPlayer 由底层 SurfaceView 驱动，无需手动更新 Surface 尺寸属性
    }

    override fun loadVideo(url: String, headers: Map<String, String>, resumePos: Long) {
        progressTickerJob?.cancel()
        isPrepared = false
        pendingResumePositionMs = resumePos

        // 异步串行重置并创建新 MediaPlayer
        mediaPlayerScope.launch {
            try {
                safeReleaseMediaPlayerInternal()

                val player = MediaPlayer().apply {
                    setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )

                    synchronized(surfaceLock) {
                        attachedHolder?.let { h ->
                            if (h.surface.isValid) {
                                try { setDisplay(h) } catch (_: Throwable) {}
                            }
                        } ?: attachedSurface?.let { surf ->
                            if (surf.isValid) {
                                try { setSurface(surf) } catch (_: Throwable) {}
                            }
                        }
                    }

                    if (url.startsWith("/") || File(url).exists()) {
                        setDataSource(url)
                    } else {
                        val uri = Uri.parse(url)
                        if (headers.isNotEmpty()) {
                            setDataSource(context, uri, headers)
                        } else {
                            setDataSource(context, uri)
                        }
                    }

                    setOnVideoSizeChangedListener { _, width, height ->
                        ZakoLog.d(TAG, "MediaPlayer video size changed: ${width}x$height")
                        if (width > 0 && height > 0) {
                            callback.onVideoSizeChanged(width, height)
                        }
                    }

                    setOnPreparedListener { mp ->
                        isPrepared = true
                        synchronized(surfaceLock) {
                            try {
                                attachedHolder?.let { h ->
                                    if (h.surface.isValid) mp.setDisplay(h)
                                } ?: attachedSurface?.let { s ->
                                    if (s.isValid) mp.setSurface(s)
                                }
                            } catch (e: Exception) {
                                ZakoLog.w(TAG, "setOnPreparedListener surface binding error", e)
                            }
                        }
                        val dur = mp.duration.toLong().coerceAtLeast(0L)
                        val seekPos = pendingResumePositionMs
                        ZakoLog.i(TAG, "MediaPlayer prepared, duration: ${dur}ms, resumePosition: ${seekPos}ms")

                        callback.onPositionChanged(seekPos, dur)
                        callback.onBufferingStateChanged(false)
                        callback.onPlaybackStateChanged(true)

                        if (seekPos > 0L && seekPos < dur) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp.seekTo(seekPos, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(seekPos.toInt())
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val params = mp.playbackParams
                                params.speed = currentSpeed
                                mp.playbackParams = params
                            } catch (e: Exception) {
                                ZakoLog.w(TAG, "Failed to set playback params speed", e)
                            }
                        }
                        mp.start()
                        startProgressTicker()
                    }

                    setOnBufferingUpdateListener { _, percent ->
                        val dur = try { duration.toLong() } catch (_: Throwable) { 0L }
                        if (dur > 0L && percent > 0) {
                            val bufMs = (dur * (percent / 100f)).toLong().coerceIn(0L, dur)
                            callback.onBufferedPositionChanged(bufMs)
                        }
                    }

                    setOnInfoListener { _, what, _ ->
                        when (what) {
                            MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                                callback.onBufferingStateChanged(true)
                            }
                            MediaPlayer.MEDIA_INFO_BUFFERING_END,
                            MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                                callback.onBufferingStateChanged(false)
                            }
                        }
                        true
                    }

                    setOnCompletionListener {
                        ZakoLog.i(TAG, "MediaPlayer playback completed")
                        callback.onPlaybackStateChanged(false)
                        callback.onPlaybackCompleted()
                    }

                    setOnErrorListener { _, what, extra ->
                        ZakoLog.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        if (what == -38) {
                            return@setOnErrorListener true
                        }
                        isPrepared = false
                        callback.onBufferingStateChanged(false)
                        callback.onPlaybackStateChanged(false)
                        callback.onError("系统内核播放异常 (what=$what, extra=$extra)")
                        true
                    }

                    prepareAsync()
                }
                this@SystemPlayerEngine.mediaPlayer = player
            } catch (e: Exception) {
                isPrepared = false
                ZakoLog.e(TAG, "Failed to initialize MediaPlayer for $url", e)
                callback.onBufferingStateChanged(false)
                callback.onError(e.message ?: "初始化系统播放器失败")
            }
        }
    }

    override fun play() {
        postMediaPlayerOp { mp ->
            if (isPrepared) {
                try {
                    mp.start()
                    callback.onPlaybackStateChanged(true)
                    startProgressTicker()
                } catch (e: Exception) {
                    ZakoLog.e(TAG, "play error", e)
                }
            }
        }
    }

    override fun pause() {
        postMediaPlayerOp { mp ->
            if (isPrepared) {
                try {
                    mp.pause()
                    callback.onPlaybackStateChanged(false)
                } catch (e: Exception) {
                    ZakoLog.e(TAG, "pause error", e)
                }
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        inFlightSeekTargetMs = positionMs
        inFlightSeekTimestamp = System.currentTimeMillis()
        postMediaPlayerOp { mp ->
            if (isPrepared) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mp.seekTo(positionMs.toInt())
                    }
                    callback.onPositionChanged(positionMs, -1L)
                } catch (e: Exception) {
                    ZakoLog.e(TAG, "MediaPlayer seek error", e)
                }
            } else {
                pendingResumePositionMs = positionMs
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        postMediaPlayerOp { mp ->
            if (isPrepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                } catch (e: Exception) {
                    ZakoLog.w(TAG, "setPlaybackSpeed MediaPlayer error", e)
                }
            }
        }
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        // 系统内核画幅缩放由外部 MPVView/SurfaceView 的 onMeasure 计算完成
    }

    override fun setHwDecMode(mode: HwDecMode) {
        // 系统内核原生使用 MediaCodec，无需 MPV 的 hwdec 配置
    }

    override fun stop() {
        progressTickerJob?.cancel()
        postMediaPlayerOp { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.setDisplay(null)
                mp.setSurface(null)
            } catch (_: Throwable) {}
            isPrepared = false
        }
    }

    private fun safeReleaseMediaPlayerInternal() {
        val player = mediaPlayer
        mediaPlayer = null
        isPrepared = false
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Throwable) {}
            try { player.setDisplay(null) } catch (_: Throwable) {}
            try { player.setSurface(null) } catch (_: Throwable) {}
            try { player.reset() } catch (_: Throwable) {}
            try { player.release() } catch (_: Throwable) {}
            ZakoLog.d(TAG, "MediaPlayer safely released in background queue")
        }
    }

    override fun release() {
        progressTickerJob?.cancel()
        // 彻底释放后台线程与 MediaPlayer 实例，杜绝线程泄漏
        mediaPlayerScope.launch {
            safeReleaseMediaPlayerInternal()
            mediaPlayerScope.cancel()
            try {
                mediaPlayerExecutor.shutdown()
            } catch (_: Throwable) {}
        }
        synchronized(surfaceLock) {
            attachedSurface = null
            attachedHolder = null
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(250)
                if (isPrepared) {
                    mediaPlayer?.let { mp ->
                        try {
                            if (mp.isPlaying) {
                                val pos = mp.currentPosition.toLong()
                                val dur = mp.duration.toLong()

                                val seekTarget = inFlightSeekTargetMs
                                if (seekTarget >= 0L) {
                                    val elapsed = System.currentTimeMillis() - inFlightSeekTimestamp
                                    if (elapsed < 1200L) {
                                        if (kotlin.math.abs(pos - seekTarget) <= 1500L) {
                                            inFlightSeekTargetMs = -1L
                                        } else {
                                            if (dur > 0L) callback.onPositionChanged(-1L, dur)
                                            return@let
                                        }
                                    } else {
                                        inFlightSeekTargetMs = -1L
                                    }
                                }

                                callback.onPositionChanged(pos, dur)
                                callback.onPlaybackStateChanged(true)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
