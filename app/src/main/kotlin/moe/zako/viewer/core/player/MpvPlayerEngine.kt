package moe.zako.viewer.core.player

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import moe.zako.viewer.core.logger.ZakoLog
import java.util.concurrent.Executors

/**
 * libmpv 原生渲染播放引擎适配器实现 (MpvPlayerEngine)
 * 封装 libmpv JNI 调用、OpenGL/EGL Surface 渲染生命周期、新规范画幅调控与高精度异步 Seek
 */
internal class MpvPlayerEngine(
    private val context: Context,
    private val callback: EngineCallback,
    private var hwDecMode: HwDecMode = HwDecMode.MEDIACODEC_COPY
) : PlayerEngineAdapter, MPVLib.EventObserver, MPVLib.LogObserver {

    companion object {
        private const val TAG = "MpvPlayerEngine"
    }

    override val engineType: PlayerEngine = PlayerEngine.MPV

    override val isReady: Boolean
        get() = MPVLib.isReady()

    private val surfaceLock = Any()
    private var attachedSurface: Surface? = null
    private var boundMpvSurface: Surface? = null
    private var isSurfaceAttachedToMpv = false
    private var lastSurfaceWidth = 0
    private var lastSurfaceHeight = 0
    private var pendingMpvResumePosMs = 0L

    @Volatile
    private var inFlightSeekTargetMs = -1L
    @Volatile
    private var inFlightSeekTimestamp = 0L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTickerJob: Job? = null

    // 独立单线程调度器派发 mpv_command_async，严禁在主线程执行同步 Seek IPC
    private val seekExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ZakoPlayer-MPV-Seek").apply { isDaemon = true }
    }
    private val seekDispatcher = seekExecutor.asCoroutineDispatcher()
    private val seekScope = CoroutineScope(seekDispatcher + SupervisorJob())

    init {
        initMpvIfNeeded()
        MPVLib.addLogObserver(this)
    }

    fun initMpvIfNeeded(): Boolean {
        if (!MPVLib.isLoaded()) return false
        synchronized(MPVLib) {
            if (!MPVLib.isReady()) {
                try {
                    val hasSurf = attachedSurface?.isValid == true
                    ZakoLog.i(TAG, "initMpv: initializing MPVLib, hasSurface=$hasSurf")
                    MPVLib.create(context)
                    configureMpvOptions(hwDecMode)
                    MPVLib.init()
                    MPVLib.isInitialized = true
                    MPVLib.addObserver(this)
                    ZakoLog.i(TAG, "initMpv: MPVLib initialized successfully")
                } catch (e: Throwable) {
                    MPVLib.isInitialized = false
                    ZakoLog.e(TAG, "initMpv: failed to initialize MPVLib", e)
                    return false
                }
            } else {
                MPVLib.addObserver(this)
            }
            synchronized(surfaceLock) {
                val surf = attachedSurface
                if (surf != null && surf.isValid && !isSurfaceAttachedToMpv) {
                    bindSurfaceToMpv(surf)
                }
            }
        }
        return MPVLib.isReady()
    }

    private fun configureMpvOptions(hwdec: HwDecMode) {
        if (!MPVLib.isLoaded()) return
        try {
            if (!MPVLib.isReady() && attachedSurface == null) {
                MPVLib.setOptionString("vo", "null")
            }
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("gpu-api", "opengl")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("ao", "audiotrack,opensles")

            val effectiveHwdec = when (hwdec) {
                HwDecMode.MEDIACODEC, HwDecMode.MEDIACODEC_COPY, HwDecMode.AUTO -> "mediacodec-copy"
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
            MPVLib.setOptionString("msg-level", "all=warn")

            // 缓冲与解复用缓存配置
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

    override fun attachSurface(surface: Surface, holder: SurfaceHolder?) {
        synchronized(surfaceLock) {
            if (!surface.isValid) {
                ZakoLog.w(TAG, "attachSurface ignored: surface is not valid")
                return
            }
            val isSameSurface = (attachedSurface === surface)
            attachedSurface = surface

            ZakoLog.i(TAG, "attachSurface: isValid=true, isSameSurface=$isSameSurface, isBoundToMpv=$isSurfaceAttachedToMpv")
            if (initMpvIfNeeded()) {
                if (!isSurfaceAttachedToMpv || boundMpvSurface !== surface) {
                    bindSurfaceToMpv(surface)
                }
            } else {
                ZakoLog.w(TAG, "attachSurface: MPV init failed, keeping surface cached")
            }
        }
    }

    private fun bindSurfaceToMpv(surface: Surface) {
        if (!MPVLib.isReady()) {
            ZakoLog.w(TAG, "bindSurfaceToMpv skipped: MPVLib not ready")
            return
        }
        // 若当前 Surface 已成功绑定到 MPV 且 vo 仍有效，直接复用，绝不重复触发 EGL 重建
        if (isSurfaceAttachedToMpv && boundMpvSurface === surface && surface.isValid) {
            val currentVo = try { MPVLib.getPropertyString("vo") } catch (_: Throwable) { "" }
            if (!currentVo.isNullOrEmpty() && currentVo != "null") {
                ZakoLog.d(TAG, "bindSurfaceToMpv: surface already bound and vo=$currentVo, skipping duplicate attach")
                if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) {
                    try {
                        MPVLib.setPropertyString("android-surface-size", "${lastSurfaceWidth}x${lastSurfaceHeight}")
                    } catch (_: Throwable) {}
                }
                return
            }
        }

        try {
            ZakoLog.i(TAG, "bindSurfaceToMpv: attaching surface, size=${lastSurfaceWidth}x${lastSurfaceHeight}")
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
            boundMpvSurface = surface
        } catch (e: Throwable) {
            ZakoLog.e(TAG, "bindSurfaceToMpv failed", e)
        }
    }

    override fun detachSurface() {
        synchronized(surfaceLock) {
            if (attachedSurface == null && !isSurfaceAttachedToMpv) return
            ZakoLog.i(TAG, "detachSurface: detaching surface, wasBoundToMpv=$isSurfaceAttachedToMpv")
            attachedSurface = null
            boundMpvSurface = null
            isSurfaceAttachedToMpv = false

            if (MPVLib.isReady()) {
                try {
                    MPVLib.setPropertyString("vo", "null")
                } catch (_: Throwable) {}
                try {
                    MPVLib.detachSurface()
                } catch (e: Throwable) {
                    ZakoLog.e(TAG, "detachSurface MPVLib error", e)
                }
            }
        }
    }

    override fun setSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            lastSurfaceWidth = width
            lastSurfaceHeight = height
            if (MPVLib.isReady()) {
                try {
                    MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                } catch (_: Throwable) {}
            }
        }
    }

    override fun loadVideo(url: String, headers: Map<String, String>, resumePos: Long) {
        pendingMpvResumePosMs = resumePos
        try {
            if (!initMpvIfNeeded()) {
                ZakoLog.e(TAG, "loadVideo: MPV init failed for $url")
                callback.onError("MPV 内核初始化失败", isFatal = true)
                return
            }
            configureMpvOptions(hwDecMode)

            synchronized(surfaceLock) {
                val surf = attachedSurface
                if (surf != null && surf.isValid) {
                    if (!isSurfaceAttachedToMpv || boundMpvSurface !== surf) {
                        bindSurfaceToMpv(surf)
                    } else if (lastSurfaceWidth > 0 && lastSurfaceHeight > 0) {
                        try { MPVLib.setPropertyString("android-surface-size", "${lastSurfaceWidth}x${lastSurfaceHeight}") } catch (_: Throwable) {}
                    }
                } else {
                    try { MPVLib.setPropertyString("vo", "null") } catch (_: Throwable) {}
                }
                Unit
            }

            if (headers.isNotEmpty()) {
                val headerList = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
                try { MPVLib.setOptionString("http-header-fields", headerList) } catch (_: Throwable) {}
                try { MPVLib.setPropertyString("http-header-fields", headerList) } catch (_: Throwable) {}
            }

            if (resumePos > 0L) {
                val resumeSecStr = (resumePos / 1000.0).toString()
                try { MPVLib.setOptionString("start", resumeSecStr) } catch (_: Throwable) {}
                try { MPVLib.setPropertyString("start", resumeSecStr) } catch (_: Throwable) {}
                MPVLib.command(arrayOf("loadfile", url, "replace", "-1", "start=$resumeSecStr"))
            } else {
                MPVLib.command(arrayOf("loadfile", url))
            }
            play()
            startProgressTicker()
        } catch (e: Throwable) {
            ZakoLog.e(TAG, "MPV loadfile error", e)
            callback.onError(e.message ?: "加载视频失败")
        }
    }

    override fun play() {
        if (MPVLib.isReady()) {
            try {
                MPVLib.setPropertyBoolean("pause", false)
                startProgressTicker()
            } catch (_: Throwable) {}
        }
    }

    override fun pause() {
        if (MPVLib.isReady()) {
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (_: Throwable) {}
        }
    }

    override fun seekTo(positionMs: Long) {
        inFlightSeekTargetMs = positionMs
        inFlightSeekTimestamp = System.currentTimeMillis()
        val targetSec = positionMs / 1000.0
        seekScope.launch {
            if (MPVLib.isReady()) {
                try {
                    MPVLib.commandAsync(arrayOf("seek", targetSec.toString(), "absolute+keyframes"))
                } catch (e: Throwable) {
                    ZakoLog.e(TAG, "MPV seek error", e)
                }
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (MPVLib.isReady()) {
            try {
                MPVLib.setPropertyDouble("speed", speed.toDouble())
            } catch (_: Throwable) {}
        }
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        if (MPVLib.isReady()) {
            try {
                when (mode) {
                    AspectRatioMode.FIT -> {
                        MPVLib.setPropertyString("video-aspect-override", "no")
                        MPVLib.setPropertyString("video-aspect-mode", "container")
                    }
                    AspectRatioMode.FILL -> {
                        MPVLib.setPropertyString("video-aspect-override", "no")
                        MPVLib.setPropertyString("video-aspect-mode", "ignore")
                    }
                    AspectRatioMode.FIXED_16_9 -> {
                        MPVLib.setPropertyString("video-aspect-mode", "container")
                        MPVLib.setPropertyString("video-aspect-override", "1.777777")
                    }
                    AspectRatioMode.FIXED_4_3 -> {
                        MPVLib.setPropertyString("video-aspect-mode", "container")
                        MPVLib.setPropertyString("video-aspect-override", "1.333333")
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    override fun setHwDecMode(mode: HwDecMode) {
        hwDecMode = mode
        if (MPVLib.isLoaded()) {
            try {
                val effectiveHwdec = when (mode) {
                    HwDecMode.MEDIACODEC, HwDecMode.MEDIACODEC_COPY, HwDecMode.AUTO -> "mediacodec-copy"
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

    override fun stop() {
        progressTickerJob?.cancel()
        if (MPVLib.isReady()) {
            try {
                MPVLib.setPropertyBoolean("pause", true)
                MPVLib.command(arrayOf("stop"))
                MPVLib.setPropertyString("vo", "null")
                MPVLib.detachSurface()
                isSurfaceAttachedToMpv = false
                boundMpvSurface = null
            } catch (_: Throwable) {}
        }
    }

    override fun release() {
        progressTickerJob?.cancel()
        seekScope.cancel()
        try {
            seekExecutor.shutdown()
        } catch (_: Throwable) {}

        if (MPVLib.isReady()) {
            try {
                MPVLib.removeObserver(this)
                MPVLib.removeLogObserver(this)
                MPVLib.isInitialized = false
                try {
                    MPVLib.setPropertyBoolean("pause", true)
                    MPVLib.command(arrayOf("stop"))
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.detachSurface()
                } catch (_: Throwable) {}
                MPVLib.destroy()
            } catch (e: Throwable) {
                ZakoLog.e(TAG, "release MPV error", e)
            }
        } else {
            MPVLib.removeObserver(this)
            MPVLib.removeLogObserver(this)
        }
        attachedSurface = null
        boundMpvSurface = null
        isSurfaceAttachedToMpv = false
    }

    private fun dispatchPositionUpdate(posMs: Long, durMs: Long) {
        if (posMs < 0L && durMs <= 0L) return

        val seekTarget = inFlightSeekTargetMs
        if (seekTarget >= 0L) {
            val elapsed = System.currentTimeMillis() - inFlightSeekTimestamp
            if (elapsed < 1200L) {
                if (kotlin.math.abs(posMs - seekTarget) <= 1500L) {
                    inFlightSeekTargetMs = -1L
                } else {
                    // 底层进度尚未就绪（旧进度或瞬态零值），抑制该 posMs，仅更新 duration
                    if (durMs > 0L) callback.onPositionChanged(-1L, durMs)
                    return
                }
            } else {
                inFlightSeekTargetMs = -1L
            }
        }

        callback.onPositionChanged(posMs, durMs)
    }

    // --- MPV EventObserver Callbacks ---
    override fun onPropertyChange(property: String, value: Any?) {
        when (property) {
            "time-pos" -> {
                val sec = (value as? String)?.toDoubleOrNull()
                if (sec != null && !sec.isNaN() && !sec.isInfinite() && sec >= 0.0) {
                    dispatchPositionUpdate((sec * 1000.0).toLong(), -1L)
                }
            }
            "duration" -> {
                val dur = (value as? String)?.toDoubleOrNull()
                if (dur != null && !dur.isNaN() && !dur.isInfinite() && dur > 0.0) {
                    dispatchPositionUpdate(-1L, (dur * 1000.0).toLong())
                }
            }
            "pause" -> {
                val isPaused = (value as? String) == "yes"
                callback.onPlaybackStateChanged(!isPaused)
            }
            "paused-for-cache" -> {
                val isBuffering = (value as? String) == "yes"
                callback.onBufferingStateChanged(isBuffering)
            }
            "demuxer-cache-time" -> {
                val sec = (value as? String)?.toDoubleOrNull()
                if (sec != null && !sec.isNaN() && !sec.isInfinite() && sec >= 0.0) {
                    callback.onBufferedPositionChanged((sec * 1000.0).toLong())
                }
            }
        }
    }

    override fun onPlaybackStateChange(isPlaying: Boolean) {
        callback.onPlaybackStateChanged(isPlaying)
    }

    override fun onPlaybackEnd() {
        callback.onPlaybackCompleted()
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

    // --- MPV LogObserver (精确过滤 mediacodec-copy 预期内噪声) ---
    override fun onLogMessage(prefix: String, level: Int, text: String) {
        // 仅限精确匹配 mediacodec-copy 下的预期噪声，严禁宽泛过滤其他丢帧或硬解日志
        if (prefix == "ffmpeg/video" && text.contains("Both surface and native_window are NULL")) {
            // 预期内 Surface 初始化前阶段静默跳过
            return
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(250)
                if (MPVLib.isReady()) {
                    try {
                        val timePos = MPVLib.getPropertyDouble("time-pos")
                        val duration = MPVLib.getPropertyDouble("duration")
                        val paused = MPVLib.getPropertyBoolean("pause")
                        val pausedForCache = MPVLib.getPropertyBoolean("paused-for-cache")
                        val demuxCache = MPVLib.getPropertyDouble("demuxer-cache-time")
                        val vw = MPVLib.getPropertyInt("video-params/w") ?: 0
                        val vh = MPVLib.getPropertyInt("video-params/h") ?: 0
                        val eof = MPVLib.getPropertyBoolean("eof-reached") ?: false

                        val durMs = if (duration != null && !duration.isNaN() && !duration.isInfinite() && duration > 0.0) {
                            (duration * 1000.0).toLong()
                        } else -1L

                        val rawPosMs = if (timePos != null && !timePos.isNaN() && !timePos.isInfinite() && timePos >= 0.0) {
                            (timePos * 1000.0).toLong()
                        } else -1L

                        val effectivePos = if (rawPosMs >= 0L) {
                            if (pendingMpvResumePosMs > 0L && rawPosMs == 0L) {
                                pendingMpvResumePosMs
                            } else {
                                if (rawPosMs > 0L) pendingMpvResumePosMs = 0L
                                rawPosMs
                            }
                        } else -1L

                        val isPlaying = paused == false && !eof
                        val isBuffering = pausedForCache == true || (durMs == 0L && !eof)
                        val cacheSec = demuxCache ?: 0.0
                        val bufMs = if (durMs > 0L && effectivePos >= 0L) (effectivePos + (cacheSec * 1000.0).toLong()).coerceIn(0L, durMs) else 0L

                        if (effectivePos >= 0L || durMs > 0L) {
                            dispatchPositionUpdate(effectivePos, durMs)
                        }
                        callback.onPlaybackStateChanged(isPlaying)
                        callback.onBufferingStateChanged(isBuffering)
                        if (bufMs > 0L) {
                            callback.onBufferedPositionChanged(bufMs)
                        }
                        if (vw > 0 && vh > 0) {
                            callback.onVideoSizeChanged(vw, vh)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
