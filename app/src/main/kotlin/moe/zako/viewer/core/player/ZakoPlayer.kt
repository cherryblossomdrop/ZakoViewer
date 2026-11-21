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
    private var lastSurfaceWidth = 0
    private var lastSurfaceHeight = 0
    private var isSurfaceAttachedToMpv = false
    private var pendingMpvResumePosMs = 0L

    fun setSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            lastSurfaceWidth = width
            lastSurfaceHeight = height
            val hasSurf = attachedSurface?.isValid == true
            ZakoLog.i(TAG, "setSurfaceSize: ${width}x${height}, isMpvInitialized=${MPVLib.isInitialized}, hasSurface=$hasSurf, isBoundToMpv=$isSurfaceAttachedToMpv")
            if (isMpvActive()) {
                try {
                    MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                } catch (_: Throwable) {}
            }
        }
    }

    private var lastSeekTimestamp = 0L
    private var lastBufferingToastTimestamp = 0L
    private val seekExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ZakoPlayer-Seek").apply { isDaemon = true }
    }
    private val seekDispatcher = seekExecutor.asCoroutineDispatcher()
    private val seekScope = CoroutineScope(seekDispatcher + SupervisorJob())

    // 独立单线程执行器，专门派发跨进程 MediaPlayer IPC 调用，严禁在 UI 主线程执行阻塞操作
    private val mediaPlayerExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ZakoPlayer-MediaPlayer-IPC").apply { isDaemon = true }
    }
    private val mediaPlayerDispatcher = mediaPlayerExecutor.asCoroutineDispatcher()
    private val mediaPlayerScope = CoroutineScope(mediaPlayerDispatcher + SupervisorJob())

    private fun safeAsyncMediaPlayerOp(action: (MediaPlayer) -> Unit) {
        val player = mediaPlayer ?: return
        mediaPlayerScope.launch {
            try {
                action(player)
            } catch (e: Throwable) {
                ZakoLog.w(TAG, "Async MediaPlayer operation failed", e)
            }
        }
    }

    private fun safeAsyncReleaseMediaPlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        isMediaPlayerPrepared = false
        if (player != null) {
            // 先在主线程同步断开 Surface 绑定并尽量 stop，确保立即解绑 BufferQueue，彻底杜绝与 MPV EGL Surface 争抢
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Throwable) {}
            try {
                player.setDisplay(null)
            } catch (_: Throwable) {}
            try {
                player.setSurface(null)
            } catch (_: Throwable) {}

            mediaPlayerScope.launch {
                try {
                    try { player.reset() } catch (_: Throwable) {}
                    try { player.release() } catch (_: Throwable) {}
                    ZakoLog.d(TAG, "MediaPlayer released asynchronously in background thread")
                } catch (e: Throwable) {
                    ZakoLog.w(TAG, "Async release MediaPlayer error", e)
                }
            }
        }
    }

    private fun isMpvActive(): Boolean {
        return when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isReady()
            PlayerEngine.SYSTEM -> false
            PlayerEngine.AUTO -> MPVLib.isReady()
        }
    }

    private fun isMpvPreferred(): Boolean {
        return (_state.value.engine == PlayerEngine.MPV || _state.value.engine == PlayerEngine.AUTO) && MPVLib.isLoaded()
    }

    private fun initMpvIfNeeded(): Boolean {
        if (!MPVLib.isLoaded()) return false
        synchronized(MPVLib) {
            if (!MPVLib.isReady()) {
                try {
                    val hasSurf = attachedSurface?.isValid == true
                    ZakoLog.i(TAG, "initMpv: initializing MPVLib instance, hasSurface=$hasSurf")
                    MPVLib.create(context)
                    configureMpvOptions(_state.value.hwDecMode)
                    MPVLib.init()
                    MPVLib.isInitialized = true
                    MPVLib.addObserver(this)
                    ZakoLog.i(
                        TAG,
                        "initMpv: MPVLib initialized successfully, isMpvInitialized=${MPVLib.isInitialized}, hasSurface=$hasSurf, isBoundToMpv=$isSurfaceAttachedToMpv"
                    )
                } catch (e: Throwable) {
                    MPVLib.isInitialized = false
                    ZakoLog.e(TAG, "initMpv: failed to initialize MPVLib", e)
                    return false
                }
            } else {
                MPVLib.addObserver(this)
            }
            // 若在 MPV 初始化前已有有效 Surface，立即进行补绑激活
            synchronized(surfaceLock) {
                val surf = attachedSurface
                if (surf != null && surf.isValid && !isSurfaceAttachedToMpv) {
                    bindSurfaceToMpv(surf)
                }
            }
        }
        return MPVLib.isReady()
    }

    private fun bindSurfaceToMpv(surface: Surface) {
        if (!MPVLib.isReady()) {
            ZakoLog.w(TAG, "bindSurfaceToMpv: skipped, MPVLib is not ready")
            return
        }
        try {
            ZakoLog.i(TAG, "bindSurfaceToMpv: attaching surface to MPVLib, surfaceSize=${lastSurfaceWidth}x${lastSurfaceHeight}")
            MPVLib.attachSurface(surface)
            if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) {
                try {
                    MPVLib.setPropertyString("android-surface-size", "${lastSurfaceWidth}x${lastSurfaceHeight}")
                } catch (_: Throwable) {}
            }
            try {
                MPVLib.setPropertyString("force-window", "yes")
                MPVLib.setPropertyString("vo", "gpu")
            } catch (_: Throwable) {}
            isSurfaceAttachedToMpv = true
            val currentVo = try { MPVLib.getPropertyString("vo") } catch (_: Throwable) { "unknown" }
            ZakoLog.i(
                TAG,
                "bindSurfaceToMpv: complete, isMpvInitialized=${MPVLib.isInitialized}, hasSurface=${attachedSurface?.isValid == true}, isBoundToMpv=$isSurfaceAttachedToMpv, vo=$currentVo, surfaceSize=${lastSurfaceWidth}x${lastSurfaceHeight}"
            )
        } catch (e: Throwable) {
            ZakoLog.e(TAG, "bindSurfaceToMpv failed", e)
        }
    }

    init {
        initMpvIfNeeded()
    }

    /**
     * Configure high performance and streaming options based on NextPlayerCloud implementation.
     */
    private fun configureMpvOptions(hwdec: HwDecMode) {
        if (!MPVLib.isLoaded()) return
        try {
            // Video output & hardware decoding
            // 仅在 MPV 尚未就绪且尚未关联有效 Surface 时，初始化阶段预设 vo="null"，避免启动阶段报 Missing surface pointer
            if (!MPVLib.isReady() && attachedSurface == null) {
                MPVLib.setOptionString("vo", "null")
            }
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("gpu-api", "opengl")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("ao", "audiotrack,opensles")

            // vo=gpu 模式下必须搭配 mediacodec-copy（或软解 no），不要使用零拷贝的 mediacodec 直接输出，以保证 OpenGL ES 纹理正常采样
            val effectiveHwdec = when (hwdec) {
                HwDecMode.MEDIACODEC -> "mediacodec-copy"
                HwDecMode.MEDIACODEC_COPY -> "mediacodec-copy"
                HwDecMode.AUTO -> "mediacodec-copy"
                HwDecMode.OFF -> "no"
            }
            MPVLib.setOptionString("hwdec", effectiveHwdec)
            if (MPVLib.isReady()) {
                MPVLib.setPropertyString("hwdec", effectiveHwdec)
            }
            MPVLib.setOptionString("hwdec-codecs", "all")
            MPVLib.setOptionString("vd-lavc-fast", "yes")
            MPVLib.setOptionString("vd-lavc-threads", "0")
            MPVLib.setOptionString("vd-lavc-dr", "yes")
            MPVLib.setOptionString("force-window", "yes")
            // 收敛日志级别为 all=warn，避免播放时高频产生 seek 等调试输出
            MPVLib.setOptionString("msg-level", "all=warn")

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
        val currentPosition = if (_state.value.engine == PlayerEngine.SYSTEM) {
            try {
                mediaPlayer?.takeIf { isMediaPlayerPrepared }?.currentPosition?.toLong()?.coerceAtLeast(0L)
            } catch (_: Throwable) { null } ?: _state.value.currentPositionMs
        } else {
            _state.value.currentPositionMs
        }
        ZakoLog.i(TAG, "Switching player engine to: ${engine.name}, currentPosition: ${currentPosition}ms")
        _state.value = _state.value.copy(engine = engine, currentPositionMs = currentPosition)
        val curUrl = _state.value.activeUrl
        if (curUrl.isNotEmpty()) {
            loadVideo(curUrl, _state.value.title, activeHeaders, resumePos = currentPosition)
        }
    }

    fun setHwDecMode(mode: HwDecMode) {
        _state.value = _state.value.copy(hwDecMode = mode)
        if (MPVLib.isLoaded()) {
            try {
                val effectiveHwdec = when (mode) {
                    HwDecMode.MEDIACODEC -> "mediacodec-copy"
                    HwDecMode.MEDIACODEC_COPY -> "mediacodec-copy"
                    HwDecMode.AUTO -> "mediacodec-copy"
                    HwDecMode.OFF -> "no"
                }
                MPVLib.setOptionString("hwdec", effectiveHwdec)
                if (MPVLib.isReady()) {
                    MPVLib.setPropertyString("hwdec", effectiveHwdec)
                }
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
            val isSameSurface = (attachedSurface === surface)
            attachedSurface = surface
            attachedHolder = holder
            val mpvPreferred = isMpvPreferred()
            ZakoLog.i(
                TAG,
                "attachSurface: surface received, isValid=true, isSameSurface=$isSameSurface, engine=${_state.value.engine}, mpvPreferred=$mpvPreferred, isMpvInitialized=${MPVLib.isInitialized}, hasSurface=true, isBoundToMpv=$isSurfaceAttachedToMpv"
            )

            if (mpvPreferred) {
                if (initMpvIfNeeded()) {
                    bindSurfaceToMpv(surface)
                } else {
                    ZakoLog.w(TAG, "attachSurface: MPV preferred but init failed, keeping surface cached")
                }
            } else {
                // 严禁在 UI 主线程执行任何阻塞 IPC 操作，派发至后台单线程异步处理
                safeAsyncMediaPlayerOp { mp ->
                    try {
                        if (holder != null && holder.surface.isValid) {
                            mp.setDisplay(holder)
                            ZakoLog.d(TAG, "attachSurface: MediaPlayer display bound successfully")
                        } else if (surface.isValid) {
                            mp.setSurface(surface)
                            ZakoLog.d(TAG, "attachSurface: MediaPlayer surface bound successfully")
                        }
                    } catch (e: Throwable) {
                        ZakoLog.w(TAG, "attachSurface MediaPlayer error", e)
                    }
                }
            }
        }
    }

    fun detachSurface() {
        synchronized(surfaceLock) {
            val wasBound = isSurfaceAttachedToMpv
            ZakoLog.i(TAG, "detachSurface: detaching surface, wasBoundToMpv=$wasBound, isMpvInitialized=${MPVLib.isInitialized}")
            attachedSurface = null
            attachedHolder = null
            isSurfaceAttachedToMpv = false

            if (isMpvPreferred() && MPVLib.isReady()) {
                try {
                    MPVLib.setPropertyString("vo", "null")
                } catch (_: Throwable) {}
                try {
                    MPVLib.detachSurface()
                } catch (e: Throwable) {
                    ZakoLog.e(TAG, "detachSurface MPVLib error", e)
                }
            } else {
                // 严禁在 UI 主线程直接调用 mediaPlayer.setDisplay(null) 或持锁等待 mediaPlayer 状态！
                // 派发到独立的后台单线程执行器进行非阻塞处理，彻底消除 surfaceDestroyed 卡死主线程
                safeAsyncMediaPlayerOp { mp ->
                    try {
                        if (mp.isPlaying) {
                            mp.pause()
                        }
                    } catch (e: Throwable) {
                        ZakoLog.w(TAG, "detachSurface MediaPlayer pause error", e)
                    }
                }
            }
        }
    }

    fun loadVideo(url: String, title: String, headers: Map<String, String> = emptyMap(), resumePos: Long = -1L) {
        activeHeaders = headers
        val resumePosition = if (resumePos >= 0L) {
            resumePos
        } else if (_state.value.activeUrl.isNotEmpty() && _state.value.activeUrl != url && _state.value.currentPositionMs > 0L) {
            _state.value.currentPositionMs
        } else {
            0L
        }
        val shouldUseMpv = isMpvPreferred()
        val hasSurfInitial = attachedSurface?.isValid == true
        ZakoLog.i(
            TAG,
            "loadVideo: $title on $url, resumePosition: ${resumePosition}ms, engine=${_state.value.engine}, shouldUseMpv=$shouldUseMpv, isMpvInitialized=${MPVLib.isInitialized}, hasSurface=$hasSurfInitial, isBoundToMpv=$isSurfaceAttachedToMpv"
        )

        _state.value = _state.value.copy(
            activeUrl = url,
            title = title,
            isBuffering = true,
            isPlaying = false,
            currentPositionMs = resumePosition
        )

        // 只要 libmpv 可用且选中 MPV/AUTO，全链路走 libmpv 渲染与播放，并且直接使用原始 URL 进行原生异步拉流
        if (shouldUseMpv) {
            // Stop and release previous MediaPlayer asynchronously in background
            safeAsyncReleaseMediaPlayer()
            pendingMpvResumePosMs = resumePosition

            try {
                if (!initMpvIfNeeded()) {
                    ZakoLog.w(TAG, "loadVideo: MPV init failed, falling back to MediaPlayer")
                    startMediaPlayerPlayback(url, headers, resumePosition)
                    return
                }
                configureMpvOptions(_state.value.hwDecMode)

                // 检查当前是否已有有效 Surface：若已有有效 Surface 必须立即绑定并激活 vo=gpu
                synchronized(surfaceLock) {
                    val surf = attachedSurface
                    val hasValidSurface = surf != null && surf.isValid
                    ZakoLog.i(TAG, "loadVideo MPV surface check: hasValidSurface=$hasValidSurface, isBoundToMpv=$isSurfaceAttachedToMpv")
                    if (hasValidSurface) {
                        bindSurfaceToMpv(surf!!)
                    } else {
                        // 暂无有效 Surface，先设为 null，待 Surface 真正创建并回调 attachSurface 时再切 gpu
                        try { MPVLib.setPropertyString("vo", "null") } catch (_: Throwable) {}
                    }
                }

                if (headers.isNotEmpty()) {
                    val headerList = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
                    try { MPVLib.setOptionString("http-header-fields", headerList) } catch (_: Throwable) {}
                    try { MPVLib.setPropertyString("http-header-fields", headerList) } catch (_: Throwable) {}
                }
                if (resumePosition > 0L) {
                    val resumeSecStr = (resumePosition / 1000.0).toString()
                    try { MPVLib.setOptionString("start", resumeSecStr) } catch (_: Throwable) {}
                    try { MPVLib.setPropertyString("start", resumeSecStr) } catch (_: Throwable) {}
                    MPVLib.command(arrayOf("loadfile", url, "replace", "-1", "start=$resumeSecStr"))
                } else {
                    MPVLib.command(arrayOf("loadfile", url))
                }
                val currentVo = try { MPVLib.getPropertyString("vo") } catch (_: Throwable) { "unknown" }
                val hasSurfFinal = attachedSurface?.isValid == true
                ZakoLog.i(
                    TAG,
                    "loadVideo MPV ready: isMpvInitialized=${MPVLib.isInitialized}, hasSurface=$hasSurfFinal, isBoundToMpv=$isSurfaceAttachedToMpv, vo=$currentVo, surfaceSize=${lastSurfaceWidth}x${lastSurfaceHeight}"
                )
            } catch (e: Throwable) {
                ZakoLog.e(TAG, "MPV loadfile error", e)
            }
            play()
            startProgressTicker()
        } else {
            // Stop MPV if running
            if (MPVLib.isReady()) {
                try {
                    MPVLib.command(arrayOf("stop"))
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.detachSurface()
                    isSurfaceAttachedToMpv = false
                } catch (_: Throwable) {}
            }
            startMediaPlayerPlayback(url, headers, resumePosition)
        }
    }

    private fun startMediaPlayerPlayback(url: String, headers: Map<String, String>, resumePositionMs: Long) {
        progressTickerJob?.cancel()
        isMediaPlayerPrepared = false
        pendingResumePositionMs = resumePositionMs
        safeAsyncReleaseMediaPlayer()

        mediaPlayerScope.launch {
            try {
                val player = MediaPlayer().apply {
                    setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )

                    synchronized(surfaceLock) {
                        attachedSurface?.let { surf ->
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
                this@ZakoPlayer.mediaPlayer = player
            } catch (e: Exception) {
                isMediaPlayerPrepared = false
                ZakoLog.e(TAG, "Failed to initialize MediaPlayer for $url", e)
                _state.value = _state.value.copy(isBuffering = false)
            }
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(250)
                if (isMpvActive()) {
                    try {
                        val timePos = MPVLib.getPropertyDouble("time-pos")
                        val duration = MPVLib.getPropertyDouble("duration")
                        val paused = MPVLib.getPropertyBoolean("pause")
                        val pausedForCache = MPVLib.getPropertyBoolean("paused-for-cache")
                        val demuxCache = MPVLib.getPropertyDouble("demuxer-cache-time")
                        val vw = MPVLib.getPropertyInt("video-params/w") ?: 0
                        val vh = MPVLib.getPropertyInt("video-params/h") ?: 0
                        val eof = MPVLib.getPropertyBoolean("eof-reached") ?: false

                        if (timePos != null || duration != null) {
                            val posMs = ((timePos ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
                            val durMs = ((duration ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
                            val isPlaying = paused == false && !eof
                            val isBuffering = pausedForCache == true || (durMs == 0L && !eof)
                            val cacheSec = demuxCache ?: 0.0
                            val bufMs = if (durMs > 0L) (posMs + (cacheSec * 1000.0).toLong()).coerceIn(0L, durMs) else 0L

                            val effectivePos = if (pendingMpvResumePosMs > 0L && posMs == 0L) {
                                pendingMpvResumePosMs
                            } else {
                                if (posMs > 0L) pendingMpvResumePosMs = 0L
                                posMs
                            }

                            _state.value = _state.value.copy(
                                currentPositionMs = effectivePos,
                                durationMs = if (durMs > 0L) durMs else _state.value.durationMs,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                bufferedPositionMs = maxOf(bufMs, _state.value.bufferedPositionMs),
                                videoWidth = if (vw > 0) vw else _state.value.videoWidth,
                                videoHeight = if (vh > 0) vh else _state.value.videoHeight
                            )
                        }
                    } catch (_: Exception) {}
                } else if (isMediaPlayerPrepared) {
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
        if (isMpvActive()) {
            try {
                MPVLib.setPropertyBoolean("pause", false)
                startProgressTicker()
            } catch (_: Throwable) {}
        } else {
            safeAsyncMediaPlayerOp { mp ->
                if (isMediaPlayerPrepared) {
                    try {
                        mp.start()
                        startProgressTicker()
                    } catch (e: Exception) {
                        ZakoLog.e(TAG, "play error", e)
                    }
                }
            }
        }
    }

    fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
        if (isMpvActive()) {
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (_: Throwable) {}
        } else {
            safeAsyncMediaPlayerOp { mp ->
                if (isMediaPlayerPrepared) {
                    try {
                        mp.pause()
                    } catch (e: Exception) {
                        ZakoLog.e(TAG, "pause error", e)
                    }
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
        val isReady = isMpvActive() || isMediaPlayerPrepared

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

        val shouldUseMpv = isMpvActive()

        seekScope.launch {
            if (shouldUseMpv) {
                try {
                    // 严禁在 Android UI 主线程执行同步网络 Seek：统一改用 mpv_command_async (seek, targetSeconds, absolute+keyframes)
                    MPVLib.commandAsync(arrayOf("seek", targetSec.toString(), "absolute+keyframes"))
                } catch (e: Throwable) {
                    ZakoLog.e(TAG, "MPV seek error", e)
                }
            } else {
                safeAsyncMediaPlayerOp { mp ->
                    if (isMediaPlayerPrepared) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp.seekTo(targetMs, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(targetMs.toInt())
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
        if (isMpvActive()) {
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
        if (isMpvActive()) {
            try {
                MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
            } catch (_: Throwable) {}
        } else {
            safeAsyncMediaPlayerOp { mp ->
                if (isMediaPlayerPrepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val params = mp.playbackParams
                        params.speed = targetSpeed
                        mp.playbackParams = params
                    } catch (e: Exception) {
                        ZakoLog.w(TAG, "setSpeedBoost MediaPlayer error", e)
                    }
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _state.value = _state.value.copy(
            normalSpeed = speed,
            currentSpeed = if (_state.value.isSpeedBoosted) _state.value.boostSpeed else speed
        )
        val targetSpeed = _state.value.currentSpeed
        if (isMpvActive()) {
            try {
                MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
            } catch (_: Throwable) {}
        } else {
            safeAsyncMediaPlayerOp { mp ->
                if (isMediaPlayerPrepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val params = mp.playbackParams
                        params.speed = targetSpeed
                        mp.playbackParams = params
                    } catch (e: Exception) {
                        ZakoLog.w(TAG, "setPlaybackSpeed MediaPlayer error", e)
                    }
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

    override fun onFileLoaded() {
        val pending = pendingMpvResumePosMs
        if (pending > 0L) {
            val sec = pending / 1000.0
            ZakoLog.i(TAG, "MPV file loaded event: seeking to resumePosition: ${pending}ms (${sec}s)")
            try {
                MPVLib.command(arrayOf("seek", sec.toString(), "absolute"))
            } catch (e: Throwable) {
                ZakoLog.w(TAG, "Seek on file loaded failed", e)
            }
            pendingMpvResumePosMs = 0L
        }
    }

    fun release() {
        isMediaPlayerPrepared = false
        progressTickerJob?.cancel()
        seekScope.cancel()
        try {
            seekExecutor.shutdown()
        } catch (_: Exception) {}
        safeAsyncReleaseMediaPlayer()
        try {
            mediaPlayerExecutor.shutdown()
        } catch (_: Exception) {}
        attachedSurface = null
        attachedHolder = null
        isSurfaceAttachedToMpv = false
        if (MPVLib.isReady()) {
            try {
                MPVLib.removeObserver(this)
                MPVLib.isInitialized = false
                try { MPVLib.setPropertyString("vo", "null") } catch (_: Throwable) {}
                MPVLib.destroy()
            } catch (e: Throwable) {
                ZakoLog.e(TAG, "release MPV error", e)
            }
        }
    }
}

typealias NeoPlayer = ZakoPlayer
