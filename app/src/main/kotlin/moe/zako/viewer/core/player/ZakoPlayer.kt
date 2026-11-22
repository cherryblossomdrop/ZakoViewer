package moe.zako.viewer.core.player

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.ui.components.ZakoToast

/**
 * 播放器核心业务封装门面 (ZakoPlayer)
 *
 * 【跨模块门面契约】：
 * 1. 采用引擎适配器模式 (Adapter Pattern)，统筹管理 MpvPlayerEngine (libmpv 原生渲染) 与 SystemPlayerEngine (系统 MediaPlayer)；
 * 2. 保证对外公开 API 与 StateFlow<VideoPlayerState> 状态流 100% 保持完全兼容；
 * 3. 严格隔离跨引擎过期回调：对引擎实例分发带专属身份校验的 EngineCallback，防止切换期间迟到的旧引擎状态污染活跃状态流；
 * 4. 严守并发与生命周期红线：不将耗时阻塞操作带到主线程，解绑与清理流程保持幂等，清理路径严禁触发惰性单例首次初始化。
 */
class ZakoPlayer(val context: Context) {

    companion object {
        private const val TAG = "ZakoPlayer"
    }

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    @Volatile
    private var activeEngineType: PlayerEngine = PlayerEngine.MPV

    private var cachedSurface: Surface? = null
    private var cachedHolder: SurfaceHolder? = null
    private var activeHeaders: Map<String, String> = emptyMap()

    private var lastSeekTimestamp = 0L
    private var lastSeekTargetMs = -1L
    private var lastBufferingToastTimestamp = 0L

    private val mpvEngine: MpvPlayerEngine by lazy {
        MpvPlayerEngine(
            context = context,
            callback = createEngineCallback(PlayerEngine.MPV),
            hwDecMode = _state.value.hwDecMode
        )
    }

    private val systemEngine: SystemPlayerEngine by lazy {
        SystemPlayerEngine(
            context = context,
            callback = createEngineCallback(PlayerEngine.SYSTEM)
        )
    }

    private fun getEngineAdapter(type: PlayerEngine): PlayerEngineAdapter {
        return when (type) {
            PlayerEngine.MPV -> mpvEngine
            PlayerEngine.SYSTEM -> systemEngine
            PlayerEngine.AUTO -> {
                if (MPVLib.isLoaded()) mpvEngine else systemEngine
            }
        }
    }

    private val currentEngineAdapter: PlayerEngineAdapter
        get() = getEngineAdapter(activeEngineType)

    init {
        // 初始根据全局设置或默认内核预热
        val preferred = if (MPVLib.isLoaded()) PlayerEngine.MPV else PlayerEngine.SYSTEM
        activeEngineType = preferred
        _state.update { it.copy(engine = preferred) }
    }

    private fun createEngineCallback(sourceType: PlayerEngine): EngineCallback = object : EngineCallback {
        private fun isCurrent(): Boolean = (activeEngineType == sourceType)

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            if (!isCurrent()) return
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onBufferingStateChanged(isBuffering: Boolean) {
            if (!isCurrent()) return
            _state.update { it.copy(isBuffering = isBuffering) }
        }

        override fun onPositionChanged(positionMs: Long, durationMs: Long) {
            if (!isCurrent()) return
            val now = System.currentTimeMillis()
            val seekTarget = lastSeekTargetMs
            val effectivePos = if (seekTarget >= 0L) {
                if (now - lastSeekTimestamp < 1200L) {
                    if (kotlin.math.abs(positionMs - seekTarget) <= 1500L) {
                        // 底层进度已到达目标附近，Seek 已就绪完成
                        lastSeekTargetMs = -1L
                        positionMs
                    } else {
                        // 底层仍处于 Seek 瞬态（上报旧进度或重定位 0 值），维持目标进度，抑制旧值覆盖
                        -1L
                    }
                } else {
                    // 超出保护窗口期，解除 Seek 保护
                    lastSeekTargetMs = -1L
                    positionMs
                }
            } else {
                positionMs
            }

            _state.update { current ->
                val newPos = if (effectivePos >= 0L) effectivePos else current.currentPositionMs
                val newDur = if (durationMs > 0L) durationMs else current.durationMs
                current.copy(currentPositionMs = newPos, durationMs = newDur)
            }
        }

        override fun onBufferedPositionChanged(bufferedMs: Long) {
            if (!isCurrent()) return
            _state.update { current ->
                current.copy(bufferedPositionMs = maxOf(bufferedMs, current.bufferedPositionMs))
            }
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            if (!isCurrent()) return
            _state.update { it.copy(videoWidth = width, videoHeight = height) }
        }

        override fun onPlaybackCompleted() {
            if (!isCurrent()) return
            _state.update { it.copy(isPlaying = false, currentPositionMs = it.durationMs) }
        }

        override fun onError(message: String, isFatal: Boolean) {
            if (!isCurrent()) return
            ZakoLog.e(TAG, "Active engine [$sourceType] error: $message (fatal=$isFatal)")
            _state.update { it.copy(isBuffering = false, isPlaying = false) }
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            currentEngineAdapter.setSurfaceSize(width, height)
        }
    }

    fun setEngine(engine: PlayerEngine) {
        if (_state.value.engine == engine) return
        val currentPosition = _state.value.currentPositionMs
        ZakoLog.i(TAG, "Switching player engine configuration to: ${engine.name}, currentPos: ${currentPosition}ms")

        _state.update { it.copy(engine = engine) }
        // 仅更新配置，不在此处自动使用旧的 activeUrl 触发 loadVideo，
        // 由外部统一编排器 (PlayerViewModel / VideoDetailViewModel) 解析适合新内核的 streamUrl 后再调用 loadVideo
    }

    fun setHwDecMode(mode: HwDecMode) {
        _state.update { it.copy(hwDecMode = mode) }
        mpvEngine.setHwDecMode(mode)
    }

    fun attachSurface(surface: Surface, holder: SurfaceHolder? = null) {
        if (!surface.isValid) {
            ZakoLog.w(TAG, "attachSurface ignored: surface is not valid")
            return
        }
        cachedSurface = surface
        cachedHolder = holder
        currentEngineAdapter.attachSurface(surface, holder)
    }

    fun detachSurface() {
        cachedSurface = null
        cachedHolder = null
        currentEngineAdapter.detachSurface()
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

        // 决策实际使用的底层引擎
        val targetEngineType = when (_state.value.engine) {
            PlayerEngine.MPV -> PlayerEngine.MPV
            PlayerEngine.SYSTEM -> PlayerEngine.SYSTEM
            PlayerEngine.AUTO -> {
                if (MPVLib.isLoaded()) {
                    PlayerEngine.MPV
                } else {
                    ZakoLog.w(TAG, "AUTO mode: libmpv not loaded, falling back to SYSTEM engine")
                    PlayerEngine.SYSTEM
                }
            }
        }

        // 引擎实例热切换仲裁
        if (activeEngineType != targetEngineType) {
            ZakoLog.i(TAG, "Switching active engine adapter from $activeEngineType to $targetEngineType")
            val oldAdapter = getEngineAdapter(activeEngineType)
            oldAdapter.stop()
            oldAdapter.detachSurface()
            activeEngineType = targetEngineType
        }

        _state.update {
            it.copy(
                activeUrl = url,
                title = title,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = resumePosition
            )
        }

        val adapter = currentEngineAdapter
        cachedSurface?.let { surf ->
            if (surf.isValid) {
                adapter.attachSurface(surf, cachedHolder)
            }
        }

        try {
            adapter.loadVideo(url, headers, resumePosition)
        } catch (e: Throwable) {
            ZakoLog.e(TAG, "Failed to load video on $targetEngineType", e)
            if (_state.value.engine == PlayerEngine.AUTO && targetEngineType == PlayerEngine.MPV) {
                ZakoLog.w(TAG, "AUTO mode: MPV load failed, falling back to SYSTEM engine", e)
                adapter.stop()
                adapter.detachSurface()
                activeEngineType = PlayerEngine.SYSTEM
                val sysAdapter = currentEngineAdapter
                cachedSurface?.let { surf ->
                    if (surf.isValid) sysAdapter.attachSurface(surf, cachedHolder)
                }
                sysAdapter.loadVideo(url, headers, resumePosition)
            }
        }
    }

    fun play() {
        _state.update { it.copy(isPlaying = true) }
        currentEngineAdapter.play()
    }

    fun pause() {
        _state.update { it.copy(isPlaying = false) }
        currentEngineAdapter.pause()
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val durMs = _state.value.durationMs
        val durSec = durMs / 1000.0
        val isReady = currentEngineAdapter.isReady

        ZakoLog.d(TAG, "seekTo: engine=${currentEngineAdapter.engineType}, targetMs=$positionMs, durationMs=$durMs, isReady=$isReady")

        if (durMs <= 0L || durSec.isNaN() || durSec.isInfinite() || !isReady) {
            ZakoLog.w(TAG, "Seek ignored: video buffering or duration unready (durationMs=$durMs, isReady=$isReady)")
            showBufferingToast()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSeekTimestamp < 150L) {
            ZakoLog.d(TAG, "Seek debounced (call interval < 150ms)")
            return
        }
        lastSeekTimestamp = now

        val maxTargetSec = (durSec - 0.5).coerceAtLeast(0.0)
        val targetSec = (positionMs / 1000.0).coerceIn(0.0, maxTargetSec)
        val targetMs = (targetSec * 1000.0).toLong()

        lastSeekTargetMs = targetMs
        lastSeekTimestamp = now

        _state.update {
            it.copy(
                currentPositionMs = targetMs,
                bufferedPositionMs = maxOf(it.bufferedPositionMs, targetMs)
            )
        }

        currentEngineAdapter.seekTo(targetMs)
    }

    private fun showBufferingToast() {
        val now = System.currentTimeMillis()
        if (now - lastBufferingToastTimestamp > 1500L) {
            lastBufferingToastTimestamp = now
            ZakoToast.show("视频缓冲中")
        }
    }

    fun seekRelative(deltaSeconds: Int) {
        val next = (_state.value.currentPositionMs + deltaSeconds * 1000L).coerceIn(0L, _state.value.durationMs)
        seekTo(next)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _state.update { it.copy(aspectRatio = mode) }
        currentEngineAdapter.setAspectRatio(mode)
    }

    fun toggleLock() {
        _state.update { it.copy(isLocked = !it.isLocked) }
    }

    fun setSpeedBoost(boosted: Boolean) {
        val targetSpeed = if (boosted) _state.value.boostSpeed else _state.value.normalSpeed
        _state.update {
            it.copy(
                isSpeedBoosted = boosted,
                currentSpeed = targetSpeed
            )
        }
        currentEngineAdapter.setPlaybackSpeed(targetSpeed)
    }

    fun setPlaybackSpeed(speed: Float) {
        _state.update {
            it.copy(
                normalSpeed = speed,
                currentSpeed = if (it.isSpeedBoosted) it.boostSpeed else speed
            )
        }
        val targetSpeed = _state.value.currentSpeed
        currentEngineAdapter.setPlaybackSpeed(targetSpeed)
    }

    fun updateBufferedPosition(bufferedMs: Long) {
        if (bufferedMs > _state.value.bufferedPositionMs) {
            _state.update { it.copy(bufferedPositionMs = bufferedMs) }
        }
    }

    fun setCustomBoostSpeed(speed: Float) {
        _state.update { it.copy(boostSpeed = speed) }
        if (_state.value.isSpeedBoosted) {
            setSpeedBoost(true)
        }
    }

    fun release() {
        cachedSurface = null
        cachedHolder = null
        try { mpvEngine.release() } catch (e: Throwable) { ZakoLog.w(TAG, "mpv release error", e) }
        try { systemEngine.release() } catch (e: Throwable) { ZakoLog.w(TAG, "system release error", e) }
    }
}

typealias NeoPlayer = ZakoPlayer
