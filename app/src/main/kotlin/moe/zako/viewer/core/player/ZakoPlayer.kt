package moe.zako.viewer.core.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.view.Surface
import android.view.SurfaceHolder
import moe.zako.viewer.core.logger.ZakoLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

import java.io.File

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
    val engine: PlayerEngine = PlayerEngine.AUTO,
    val hwDecMode: HwDecMode = HwDecMode.MEDIACODEC
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

class ZakoPlayer(val context: Context) : MPVLib.EventObserver {
    companion object {
        private const val TAG = "ZakoPlayer"
    }

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTickerJob: Job? = null

    private var attachedSurface: Surface? = null
    private var attachedHolder: SurfaceHolder? = null
    private val surfaceLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var activeHeaders: Map<String, String> = emptyMap()
    private var isMediaPlayerPrepared = false
    private var pendingResumePositionMs = 0L

    private var lastSeekTimestamp = 0L
    private var lastBufferingToastTimestamp = 0L
    private val seekExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ZakoPlayer-Seek").apply { isDaemon = true }
    }
    private val seekDispatcher = seekExecutor.asCoroutineDispatcher()
    private val seekScope = CoroutineScope(seekDispatcher + SupervisorJob())

    init {
        if (MPVLib.isLoaded()) {
            try {
                MPVLib.create(context)
                configureMpvOptions(_state.value.hwDecMode)
                MPVLib.init()
                MPVLib.isInitialized = true
                MPVLib.addObserver(this)
            } catch (e: Throwable) {
                MPVLib.isInitialized = false
                ZakoLog.e(TAG, "Failed to initialize MPVLib", e)
            }
        }
    }

    /**
     * Configure high performance and streaming options based on NextPlayerCloud implementation.
     */
    private fun configureMpvOptions(hwdec: HwDecMode) {
        if (!MPVLib.isLoaded()) return
        try {
            // Video output & hardware decoding
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("gpu-api", "opengl")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("ao", "audiotrack,opensles")
            MPVLib.setOptionString("hwdec", hwdec.code)
            MPVLib.setOptionString("hwdec-codecs", "all")
            MPVLib.setOptionString("vd-lavc-fast", "yes")
            MPVLib.setOptionString("vd-lavc-threads", "0")
            MPVLib.setOptionString("vd-lavc-dr", "yes")

            // Streaming buffer & demuxer caching (NextPlayerCloud reference)
            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("cache-secs", "60")
            MPVLib.setOptionString("demuxer-max-bytes", "67108864") // 64 MiB
            MPVLib.setOptionString("demuxer-max-back-bytes", "33554432") // 32 MiB
            MPVLib.setOptionString("demuxer-readahead-secs", "30")
            MPVLib.setOptionString("network-timeout", "15")
            MPVLib.setOptionString("force-seekable", "yes")
            MPVLib.setOptionString("hr-seek", "default")
            MPVLib.setOptionString("stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_delay_max=2")
            MPVLib.setOptionString("audio-pitch-correction", "yes")
            MPVLib.setOptionString("keep-open", "yes")
            MPVLib.setOptionString("tls-verify", "no")
            MPVLib.setOptionString("ytdl", "no")
        } catch (e: Exception) {
            ZakoLog.e(TAG, "configureMpvOptions error", e)
        }
    }

    fun setEngine(engine: PlayerEngine) {
        if (_state.value.engine == engine) return
        ZakoLog.i(TAG, "Switching player engine to: ${engine.name}")
        _state.value = _state.value.copy(engine = engine)
        val curPos = _state.value.currentPositionMs
        val curUrl = _state.value.activeUrl
        if (curUrl.isNotEmpty()) {
            loadVideo(curUrl, _state.value.title, activeHeaders, resumePos = curPos)
        }
    }

    fun setHwDecMode(mode: HwDecMode) {
        _state.value = _state.value.copy(hwDecMode = mode)
        if (MPVLib.isLoaded()) {
            try {
                MPVLib.setOptionString("hwdec", mode.code)
                MPVLib.setPropertyString("hwdec", mode.code)
            } catch (e: Exception) {
                ZakoLog.e(TAG, "Failed to set hwdec mode", e)
            }
        }
    }

    fun attachSurface(surface: Surface, holder: SurfaceHolder? = null) {
        synchronized(surfaceLock) {
            if (!surface.isValid) {
                ZakoLog.w(TAG, "attachSurface ignored: surface is not valid")
                return
            }
            attachedSurface = surface
            attachedHolder = holder
            ZakoLog.d(TAG, "attachSurface: surface isValid=true")

            val isMpv = when (_state.value.engine) {
                PlayerEngine.MPV -> MPVLib.isReady()
                PlayerEngine.AUTO -> MPVLib.isReady()
                PlayerEngine.SYSTEM -> false
            }

            if (isMpv) {
                try {
                    ZakoLog.d(TAG, "attachSurface: binding surface to MPVLib")
                    MPVLib.attachSurface(surface)
                } catch (e: Throwable) {
                    ZakoLog.e(TAG, "attachSurface MPVLib error", e)
                }
            } else {
                // 当实际运行在 MediaPlayer 兜底模式时，严禁调用任何 MPV wid 相关方法；
                // 在 attachSurface 时先校验 surface.isValid 且播放器处于 Prepared / Started / Paused 合法状态后再绑定 setDisplay(holder)，并用 try-catch 完整包裹
                try {
                    if (isMediaPlayerPrepared && mediaPlayer != null) {
                        if (holder != null) {
                            mediaPlayer?.setDisplay(holder)
                        } else {
                            mediaPlayer?.setSurface(surface)
                        }
                        ZakoLog.d(TAG, "attachSurface: MediaPlayer display/surface bound successfully")
                    } else {
                        ZakoLog.d(TAG, "attachSurface: MediaPlayer not prepared yet, holding surface reference")
                    }
                } catch (e: Throwable) {
                    ZakoLog.w(TAG, "attachSurface MediaPlayer error", e)
                }
            }
        }
    }

    fun detachSurface() {
        synchronized(surfaceLock) {
            attachedSurface = null
            attachedHolder = null
            ZakoLog.d(TAG, "detachSurface: detaching surface")

            val isMpv = when (_state.value.engine) {
                PlayerEngine.MPV -> MPVLib.isReady()
                PlayerEngine.AUTO -> MPVLib.isReady()
                PlayerEngine.SYSTEM -> false
            }

            if (isMpv) {
                try {
                    MPVLib.detachSurface()
                } catch (e: Throwable) {
                    ZakoLog.e(TAG, "detachSurface MPVLib error", e)
                }
            } else {
                // 当实际运行在 MediaPlayer 兜底模式时：严禁在 surfaceDestroyed 中调用 mediaPlayer.setDisplay(null) 或 setSurface(null)，
                // 否则底层 Surface 正在析构时断开连接会触发系统底层 libmediaplayer.so SIGSEGV 崩溃。
                // 此处仅安全暂停播放并清除引用，待新的 Surface 出现时由 attachSurface 直接换绑即可。
                try {
                    if (_state.value.isPlaying) {
                        pause()
                    }
                } catch (e: Throwable) {
                    ZakoLog.w(TAG, "detachSurface MediaPlayer pause error", e)
                }
            }
        }
    }

    fun loadVideo(url: String, title: String, headers: Map<String, String> = emptyMap(), resumePos: Long = -1L) {
        activeHeaders = headers
        ZakoLog.i(TAG, "loadVideo: $title on $url")
        val resumePosition = if (resumePos >= 0L) {
            resumePos
        } else if (_state.value.activeUrl.isNotEmpty() && _state.value.activeUrl != url && _state.value.currentPositionMs > 0L) {
            _state.value.currentPositionMs
        } else {
            0L
        }

        _state.value = _state.value.copy(
            activeUrl = url,
            title = title,
            isBuffering = true,
            isPlaying = false,
            currentPositionMs = resumePosition
        )

        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.AUTO -> MPVLib.isReady()
            PlayerEngine.SYSTEM -> false
        }

        if (shouldUseMpv) {
            // Stop and release previous MediaPlayer
            try {
                mediaPlayer?.apply {
                    try { stop() } catch (_: Exception) {}
                    reset()
                    release()
                }
                mediaPlayer = null
            } catch (_: Exception) {}

            try {
                configureMpvOptions(_state.value.hwDecMode)
                attachedSurface?.let { surf ->
                    if (surf.isValid) {
                        MPVLib.attachSurface(surf)
                    }
                }
                if (headers.isNotEmpty()) {
                    val headerList = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
                    MPVLib.setOptionString("http-header-fields", headerList)
                }
                MPVLib.command(arrayOf("loadfile", url))
                if (resumePosition > 0L) {
                    MPVLib.command(arrayOf("seek", (resumePosition / 1000.0).toString(), "absolute"))
                }
            } catch (e: Throwable) {
                ZakoLog.e(TAG, "MPV loadfile error", e)
            }
            play()
        } else {
            // Stop MPV if running
            if (MPVLib.isReady()) {
                try {
                    MPVLib.command(arrayOf("stop"))
                    MPVLib.detachSurface()
                } catch (_: Throwable) {}
            }
            startMediaPlayerPlayback(url, headers, resumePosition)
        }
    }

    private fun startMediaPlayerPlayback(url: String, headers: Map<String, String>, resumePositionMs: Long) {
        progressTickerJob?.cancel()
        isMediaPlayerPrepared = false
        pendingResumePositionMs = resumePositionMs
        try {
            mediaPlayer?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            ZakoLog.w(TAG, "Error cleaning previous MediaPlayer", e)
        }

        try {
            val player = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                attachedSurface?.let { surf ->
                    if (surf.isValid) {
                        setSurface(surf)
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
                        _state.value = _state.value.copy(
                            videoWidth = width,
                            videoHeight = height
                        )
                    }
                }

                setOnPreparedListener { mp ->
                    isMediaPlayerPrepared = true
                    synchronized(surfaceLock) {
                        try {
                            attachedHolder?.let { h ->
                                if (h.surface.isValid) mp.setDisplay(h)
                            } ?: attachedSurface?.let { s ->
                                if (s.isValid) mp.setSurface(s)
                            }
                        } catch (e: Exception) {
                            ZakoLog.w(TAG, "setOnPreparedListener binding surface error", e)
                        }
                    }
                    val dur = mp.duration.toLong().coerceAtLeast(0L)
                    val seekPos = pendingResumePositionMs
                    ZakoLog.i(TAG, "MediaPlayer prepared, duration: ${dur}ms, resumePosition: ${seekPos}ms")
                    _state.value = _state.value.copy(
                        durationMs = dur,
                        isBuffering = false,
                        isPlaying = true
                    )
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
                            params.speed = _state.value.currentSpeed
                            mp.playbackParams = params
                        } catch (e: Exception) {
                            ZakoLog.w(TAG, "Failed to set playback params speed", e)
                        }
                    }
                    mp.start()
                    startProgressTicker()
                }

                setOnBufferingUpdateListener { _, percent ->
                    val dur = _state.value.durationMs
                    if (dur > 0L && percent > 0) {
                        val bufMs = (dur * (percent / 100f)).toLong().coerceIn(0L, dur)
                        val currentBuf = _state.value.bufferedPositionMs
                        if (bufMs > currentBuf) {
                            _state.value = _state.value.copy(bufferedPositionMs = bufMs)
                        }
                    }
                }

                setOnInfoListener { _, what, _ ->
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                            _state.value = _state.value.copy(isBuffering = true)
                        }
                        MediaPlayer.MEDIA_INFO_BUFFERING_END,
                        MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                            _state.value = _state.value.copy(isBuffering = false)
                        }
                    }
                    true
                }

                setOnCompletionListener {
                    ZakoLog.i(TAG, "MediaPlayer playback completed")
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentPositionMs = _state.value.durationMs
                    )
                }

                setOnErrorListener { _, what, extra ->
                    ZakoLog.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    if (what == -38) {
                        return@setOnErrorListener true
                    }
                    isMediaPlayerPrepared = false
                    _state.value = _state.value.copy(
                        isBuffering = false,
                        isPlaying = false
                    )
                    true
                }

                prepareAsync()
            }
            this.mediaPlayer = player
        } catch (e: Exception) {
            isMediaPlayerPrepared = false
            ZakoLog.e(TAG, "Failed to initialize MediaPlayer for $url", e)
            _state.value = _state.value.copy(isBuffering = false)
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(300)
                if (isMediaPlayerPrepared) {
                    mediaPlayer?.let { mp ->
                        try {
                            if (mp.isPlaying) {
                                val pos = mp.currentPosition.toLong()
                                val dur = mp.duration.toLong()
                                _state.value = _state.value.copy(
                                    currentPositionMs = pos,
                                    durationMs = if (dur > 0) dur else _state.value.durationMs,
                                    isPlaying = true
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun play() {
        _state.value = _state.value.copy(isPlaying = true)
        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.AUTO -> MPVLib.isReady()
            PlayerEngine.SYSTEM -> false
        }
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyBoolean("pause", false)
            } catch (_: Throwable) {}
        } else {
            if (isMediaPlayerPrepared) {
                try {
                    mediaPlayer?.start()
                    startProgressTicker()
                } catch (e: Exception) {
                    ZakoLog.e(TAG, "play error", e)
                }
            }
        }
    }

    fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.AUTO -> MPVLib.isReady()
            PlayerEngine.SYSTEM -> false
        }
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (_: Throwable) {}
        } else {
            if (isMediaPlayerPrepared) {
                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {
                    ZakoLog.e(TAG, "pause error", e)
                }
            }
        }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val durMs = _state.value.durationMs
        val durSec = durMs / 1000.0
        val isReady = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.AUTO -> MPVLib.isReady() || isMediaPlayerPrepared
            PlayerEngine.SYSTEM -> isMediaPlayerPrepared
        }

        // 起播未就绪保护与参数校验：必须确认 duration > 0（总时长已解析完成且非 NaN / Infinite）且播放器处于就绪可播状态
        if (durMs <= 0L || durSec.isNaN() || durSec.isInfinite() || !isReady) {
            ZakoLog.w(TAG, "Seek ignored: video buffering or duration unready (durationMs=$durMs, isReady=$isReady)")
            showBufferingToast()
            return
        }

        // 150ms 防抖保护，防止单次点击连续触发多次重叠 Seek
        val now = System.currentTimeMillis()
        if (now - lastSeekTimestamp < 150L) {
            ZakoLog.d(TAG, "Seek debounced (call interval < 150ms)")
            return
        }
        lastSeekTimestamp = now

        // 将目标跳转秒数限制在 [0.0, duration - 0.5] 合法范围内
        val maxTargetSec = (durSec - 0.5).coerceAtLeast(0.0)
        val targetSec = (positionMs / 1000.0).coerceIn(0.0, maxTargetSec)
        val targetMs = (targetSec * 1000.0).toLong()

        _state.value = _state.value.copy(
            currentPositionMs = targetMs,
            bufferedPositionMs = maxOf(_state.value.bufferedPositionMs, targetMs)
        )

        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.AUTO -> MPVLib.isReady()
            PlayerEngine.SYSTEM -> false
        }

        seekScope.launch {
            if (shouldUseMpv) {
                try {
                    // 严禁在 Android UI 主线程执行同步网络 Seek：统一改用 mpv_command_async (seek, targetSeconds, absolute+keyframes)
                    MPVLib.commandAsync(arrayOf("seek", targetSec.toString(), "absolute+keyframes"))
                } catch (e: Throwable) {
                    ZakoLog.e(TAG, "MPV seek error", e)
                }
            } else {
                if (isMediaPlayerPrepared) {
                    try {
                        mediaPlayer?.let { mp ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp.seekTo(targetMs, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(targetMs.toInt())
                            }
                        }
                    } catch (e: Exception) {
                        ZakoLog.e(TAG, "MediaPlayer seek error", e)
                    }
                } else {
                    pendingResumePositionMs = targetMs
                }
            }
        }
    }

    private fun showBufferingToast() {
        val now = System.currentTimeMillis()
        if (now - lastBufferingToastTimestamp > 1500L) {
            lastBufferingToastTimestamp = now
            moe.zako.viewer.ui.components.ZakoToast.show("视频缓冲中")
        }
    }

    fun seekRelative(deltaSeconds: Int) {
        val next = (_state.value.currentPositionMs + deltaSeconds * 1000L).coerceIn(0L, _state.value.durationMs)
        seekTo(next)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _state.value = _state.value.copy(aspectRatio = mode)
        if (MPVLib.isReady()) {
            try {
                val ratio = when (mode) {
                    AspectRatioMode.FIT -> "-1"
                    AspectRatioMode.FILL -> "0"
                    AspectRatioMode.FIXED_16_9 -> "1.777777"
                    AspectRatioMode.FIXED_4_3 -> "1.333333"
                }
                MPVLib.setPropertyString("video-aspect-override", ratio)
            } catch (_: Throwable) {}
        }
    }

    fun toggleLock() {
        _state.value = _state.value.copy(isLocked = !_state.value.isLocked)
    }

    fun setSpeedBoost(boosted: Boolean) {
        val targetSpeed = if (boosted) _state.value.boostSpeed else _state.value.normalSpeed
        _state.value = _state.value.copy(
            isSpeedBoosted = boosted,
            currentSpeed = targetSpeed
        )
        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.AUTO -> MPVLib.isReady()
            PlayerEngine.SYSTEM -> false
        }
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
            } catch (_: Throwable) {}
        } else {
            if (isMediaPlayerPrepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mediaPlayer?.let { mp ->
                        val params = mp.playbackParams
                        params.speed = targetSpeed
                        mp.playbackParams = params
                    }
                } catch (e: Exception) {
                    ZakoLog.w(TAG, "setSpeedBoost MediaPlayer error", e)
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _state.value = _state.value.copy(
            normalSpeed = speed,
            currentSpeed = if (_state.value.isSpeedBoosted) _state.value.boostSpeed else speed
        )
        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.AUTO -> MPVLib.isReady()
            PlayerEngine.SYSTEM -> false
        }
        val targetSpeed = _state.value.currentSpeed
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
            } catch (_: Throwable) {}
        } else {
            if (isMediaPlayerPrepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mediaPlayer?.let { mp ->
                        val params = mp.playbackParams
                        params.speed = targetSpeed
                        mp.playbackParams = params
                    }
                } catch (e: Exception) {
                    ZakoLog.w(TAG, "setPlaybackSpeed MediaPlayer error", e)
                }
            }
        }
    }

    fun updateBufferedPosition(bufferedMs: Long) {
        if (bufferedMs > _state.value.bufferedPositionMs) {
            _state.value = _state.value.copy(bufferedPositionMs = bufferedMs)
        }
    }

    fun setCustomBoostSpeed(speed: Float) {
        _state.value = _state.value.copy(boostSpeed = speed)
        if (_state.value.isSpeedBoosted) {
            setSpeedBoost(true)
        }
    }

    override fun onPropertyChange(property: String, value: Any?) {
        scope.launch {
            when (property) {
                "time-pos" -> {
                    val sec = (value as? String)?.toDoubleOrNull() ?: 0.0
                    _state.value = _state.value.copy(currentPositionMs = (sec * 1000).toLong())
                }
                "duration" -> {
                    val dur = (value as? String)?.toDoubleOrNull() ?: 0.0
                    if (!dur.isNaN() && !dur.isInfinite() && dur > 0.0) {
                        _state.value = _state.value.copy(durationMs = (dur * 1000).toLong())
                    }
                }
                "pause" -> {
                    val isPaused = (value as? String) == "yes"
                    _state.value = _state.value.copy(isPlaying = !isPaused)
                }
                "paused-for-cache" -> {
                    val isBuffering = (value as? String) == "yes"
                    _state.value = _state.value.copy(isBuffering = isBuffering)
                }
                "demuxer-cache-time" -> {
                    val sec = (value as? String)?.toDoubleOrNull() ?: 0.0
                    val dur = _state.value.durationMs
                    val pos = _state.value.currentPositionMs
                    val bufMs = (pos + (sec * 1000).toLong()).coerceIn(0L, dur)
                    val currentBuf = _state.value.bufferedPositionMs
                    if (bufMs > currentBuf || currentBuf < pos) {
                        _state.value = _state.value.copy(bufferedPositionMs = maxOf(bufMs, currentBuf))
                    }
                }
            }
        }
    }

    override fun onPlaybackStateChange(isPlaying: Boolean) {
        _state.value = _state.value.copy(isPlaying = isPlaying)
    }

    override fun onPlaybackEnd() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun release() {
        isMediaPlayerPrepared = false
        progressTickerJob?.cancel()
        seekScope.cancel()
        try {
            seekExecutor.shutdown()
        } catch (_: Exception) {}
        attachedSurface = null
        if (MPVLib.isReady()) {
            try {
                MPVLib.removeObserver(this)
                MPVLib.isInitialized = false
                MPVLib.destroy()
            } catch (e: Throwable) {
                ZakoLog.e(TAG, "release MPV error", e)
            }
        }
        try {
            mediaPlayer?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            ZakoLog.w(TAG, "release MediaPlayer error", e)
        }
    }
}

typealias NeoPlayer = ZakoPlayer
