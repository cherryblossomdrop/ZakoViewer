package moe.zako.viewer.core.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.view.Surface
import moe.zako.viewer.core.logger.NeoLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val engine: PlayerEngine = PlayerEngine.SYSTEM,
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

class NeoPlayer(val context: Context) : MPVLib.EventObserver {
    companion object {
        private const val TAG = "NeoPlayer"
    }

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTickerJob: Job? = null

    private var attachedSurface: Surface? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeHeaders: Map<String, String> = emptyMap()

    init {
        if (MPVLib.isLoaded()) {
            try {
                MPVLib.create(context)
                configureMpvOptions(_state.value.hwDecMode)
                MPVLib.init()
                MPVLib.addObserver(this)
            } catch (e: Exception) {
                NeoLog.e(TAG, "Failed to initialize MPVLib", e)
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
            MPVLib.setOptionString("audio-pitch-correction", "yes")
            MPVLib.setOptionString("keep-open", "yes")
            MPVLib.setOptionString("tls-verify", "no")
            MPVLib.setOptionString("ytdl", "no")
        } catch (e: Exception) {
            NeoLog.e(TAG, "configureMpvOptions error", e)
        }
    }

    fun setEngine(engine: PlayerEngine) {
        if (_state.value.engine == engine) return
        NeoLog.i(TAG, "Switching player engine to: ${engine.name}")
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
                NeoLog.e(TAG, "Failed to set hwdec mode", e)
            }
        }
    }

    fun attachSurface(surface: Surface) {
        if (attachedSurface == surface && surface.isValid) {
            return
        }
        attachedSurface = surface
        NeoLog.d(TAG, "attachSurface: isValid=${surface.isValid}")
        val isMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isLoaded()
            PlayerEngine.AUTO -> MPVLib.isLoaded()
            PlayerEngine.SYSTEM -> false
        }

        if (isMpv && surface.isValid) {
            try {
                NeoLog.d(TAG, "attachSurface: binding wid and surface to MPVLib")
                MPVLib.attachSurface(surface)
                MPVLib.setOptionString("wid", surface.hashCode().toString())
                MPVLib.setPropertyString("wid", surface.hashCode().toString())
                MPVLib.setOptionString("vo", "gpu")
                MPVLib.setOptionString("gpu-context", "android")
                MPVLib.setOptionString("ao", "audiotrack,opensles")
            } catch (e: Exception) {
                NeoLog.e(TAG, "attachSurface MPVLib error", e)
            }
        } else {
            try {
                if (surface.isValid) {
                    mediaPlayer?.setSurface(surface)
                }
            } catch (e: Exception) {
                NeoLog.e(TAG, "attachSurface MediaPlayer error", e)
            }
        }
    }

    fun detachSurface() {
        attachedSurface = null
        NeoLog.d(TAG, "detachSurface")
        if (MPVLib.isLoaded()) {
            try {
                MPVLib.detachSurface()
            } catch (e: Exception) {
                NeoLog.e(TAG, "detachSurface MPVLib error", e)
            }
        }
        try {
            mediaPlayer?.setSurface(null)
        } catch (e: Exception) {
            NeoLog.e(TAG, "detachSurface MediaPlayer error", e)
        }
    }

    fun loadVideo(url: String, title: String, headers: Map<String, String> = emptyMap(), resumePos: Long = -1L) {
        activeHeaders = headers
        NeoLog.i(TAG, "loadVideo: $title on $url")
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
            PlayerEngine.MPV -> MPVLib.isLoaded()
            PlayerEngine.AUTO -> MPVLib.isLoaded()
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
                        MPVLib.setOptionString("wid", surf.hashCode().toString())
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
            } catch (e: Exception) {
                NeoLog.e(TAG, "MPV loadfile error", e)
            }
            play()
        } else {
            // Stop MPV if running
            if (MPVLib.isLoaded()) {
                try {
                    MPVLib.command(arrayOf("stop"))
                    MPVLib.detachSurface()
                } catch (_: Exception) {}
            }
            startMediaPlayerPlayback(url, headers, resumePosition)
        }
    }

    private fun startMediaPlayerPlayback(url: String, headers: Map<String, String>, resumePositionMs: Long) {
        progressTickerJob?.cancel()
        try {
            mediaPlayer?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            NeoLog.w(TAG, "Error cleaning previous MediaPlayer", e)
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

                val uri = Uri.parse(url)
                if (headers.isNotEmpty()) {
                    setDataSource(context, uri, headers)
                } else {
                    setDataSource(context, uri)
                }

                setOnVideoSizeChangedListener { _, width, height ->
                    NeoLog.d(TAG, "MediaPlayer video size changed: ${width}x$height")
                    if (width > 0 && height > 0) {
                        _state.value = _state.value.copy(
                            videoWidth = width,
                            videoHeight = height
                        )
                    }
                }

                setOnPreparedListener { mp ->
                    val dur = mp.duration.toLong().coerceAtLeast(0L)
                    NeoLog.i(TAG, "MediaPlayer prepared, duration: ${dur}ms, resumePosition: ${resumePositionMs}ms")
                    _state.value = _state.value.copy(
                        durationMs = dur,
                        isBuffering = false,
                        isPlaying = true
                    )
                    if (resumePositionMs > 0L && resumePositionMs < dur) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mp.seekTo(resumePositionMs, MediaPlayer.SEEK_CLOSEST)
                        } else {
                            mp.seekTo(resumePositionMs.toInt())
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val params = mp.playbackParams
                            params.speed = _state.value.currentSpeed
                            mp.playbackParams = params
                        } catch (e: Exception) {
                            NeoLog.w(TAG, "Failed to set playback params speed", e)
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
                    NeoLog.i(TAG, "MediaPlayer playback completed")
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentPositionMs = _state.value.durationMs
                    )
                }

                setOnErrorListener { _, what, extra ->
                    NeoLog.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
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
            NeoLog.e(TAG, "Failed to initialize MediaPlayer for $url", e)
            _state.value = _state.value.copy(isBuffering = false)
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(300)
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

    fun play() {
        _state.value = _state.value.copy(isPlaying = true)
        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isLoaded()
            PlayerEngine.AUTO -> MPVLib.isLoaded()
            PlayerEngine.SYSTEM -> false
        }
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyBoolean("pause", false)
            } catch (_: Exception) {}
        } else {
            try {
                mediaPlayer?.start()
            } catch (e: Exception) {
                NeoLog.e(TAG, "play error", e)
            }
        }
    }

    fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isLoaded()
            PlayerEngine.AUTO -> MPVLib.isLoaded()
            PlayerEngine.SYSTEM -> false
        }
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (_: Exception) {}
        } else {
            try {
                mediaPlayer?.pause()
            } catch (e: Exception) {
                NeoLog.e(TAG, "pause error", e)
            }
        }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        _state.value = _state.value.copy(
            currentPositionMs = target,
            bufferedPositionMs = maxOf(_state.value.bufferedPositionMs, target)
        )
        val shouldUseMpv = when (_state.value.engine) {
            PlayerEngine.MPV -> MPVLib.isLoaded()
            PlayerEngine.AUTO -> MPVLib.isLoaded()
            PlayerEngine.SYSTEM -> false
        }
        if (shouldUseMpv) {
            try {
                MPVLib.command(arrayOf("seek", (target / 1000.0).toString(), "absolute"))
            } catch (_: Exception) {}
        } else {
            try {
                mediaPlayer?.let { mp ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(target, MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mp.seekTo(target.toInt())
                    }
                }
            } catch (e: Exception) {
                NeoLog.e(TAG, "seek error", e)
            }
        }
    }

    fun seekRelative(deltaSeconds: Int) {
        val next = (_state.value.currentPositionMs + deltaSeconds * 1000L).coerceIn(0L, _state.value.durationMs)
        seekTo(next)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _state.value = _state.value.copy(aspectRatio = mode)
        if (MPVLib.isLoaded()) {
            try {
                val ratio = when (mode) {
                    AspectRatioMode.FIT -> "-1"
                    AspectRatioMode.FILL -> "0"
                    AspectRatioMode.FIXED_16_9 -> "1.777777"
                    AspectRatioMode.FIXED_4_3 -> "1.333333"
                }
                MPVLib.setPropertyString("video-aspect-override", ratio)
            } catch (_: Exception) {}
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
            PlayerEngine.MPV -> MPVLib.isLoaded()
            PlayerEngine.AUTO -> MPVLib.isLoaded()
            PlayerEngine.SYSTEM -> false
        }
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
            } catch (_: Exception) {}
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mediaPlayer?.let { mp ->
                        val params = mp.playbackParams
                        params.speed = targetSpeed
                        mp.playbackParams = params
                    }
                } catch (e: Exception) {
                    NeoLog.w(TAG, "setSpeedBoost MediaPlayer error", e)
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
            PlayerEngine.MPV -> MPVLib.isLoaded()
            PlayerEngine.AUTO -> MPVLib.isLoaded()
            PlayerEngine.SYSTEM -> false
        }
        val targetSpeed = _state.value.currentSpeed
        if (shouldUseMpv) {
            try {
                MPVLib.setPropertyDouble("speed", targetSpeed.toDouble())
            } catch (_: Exception) {}
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mediaPlayer?.let { mp ->
                        val params = mp.playbackParams
                        params.speed = targetSpeed
                        mp.playbackParams = params
                    }
                } catch (e: Exception) {
                    NeoLog.w(TAG, "setPlaybackSpeed MediaPlayer error", e)
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
                    _state.value = _state.value.copy(durationMs = (dur * 1000).toLong())
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
        progressTickerJob?.cancel()
        attachedSurface = null
        if (MPVLib.isLoaded()) {
            try {
                MPVLib.removeObserver(this)
                MPVLib.destroy()
            } catch (_: Exception) {}
        }
        try {
            mediaPlayer?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            NeoLog.w(TAG, "release MediaPlayer error", e)
        }
    }
}
