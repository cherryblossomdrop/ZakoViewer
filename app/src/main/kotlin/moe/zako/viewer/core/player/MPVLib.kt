package moe.zako.viewer.core.player

import android.content.Context
import android.view.Surface
import moe.zako.viewer.core.logger.ZakoLog

/**
 * Native JNI bridge for libmpv, designed following NextPlayerCloud / NeoPlayer architecture.
 */
object MPVLib {
    private const val TAG = "MPVLib"
    private var isNativeLoaded = false
    @Volatile var isInitialized = false

    private val FFMPEG_DEPENDENCIES = listOf(
        "c++_shared",
        "avutil",
        "swresample",
        "swscale",
        "avcodec",
        "avformat",
        "avfilter",
        "avdevice",
        "mpv",
        "player"
    )

    init {
        var mpvLoaded = false
        var playerLoaded = false

        for (lib in FFMPEG_DEPENDENCIES) {
            try {
                System.loadLibrary(lib)
                ZakoLog.i(TAG, "Native library loaded: lib$lib.so")
                if (lib == "mpv") mpvLoaded = true
                if (lib == "player") playerLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                if (lib == "mpv" || lib == "player") {
                    ZakoLog.w(TAG, "Native library lib$lib.so not found: ${e.message}")
                }
            } catch (t: Throwable) {
                ZakoLog.e(TAG, "Error loading native library lib$lib.so: ${t.message}", t)
            }
        }

        // Only mark native MPV available if both libmpv and player bridge were loaded successfully
        isNativeLoaded = mpvLoaded && playerLoaded
        if (isNativeLoaded) {
            ZakoLog.i(TAG, "libmpv and native player libraries loaded successfully")
        } else {
            ZakoLog.w(TAG, "libmpv native runtime unavailable (mpv=$mpvLoaded, player=$playerLoaded), will fallback to MediaPlayer")
        }
    }

    fun isLoaded(): Boolean = isNativeLoaded
    fun isReady(): Boolean = isNativeLoaded && isInitialized

    // Native declarations
    external fun create(ctx: Context)
    external fun init()
    external fun destroy()
    external fun attachSurface(surface: Surface)
    external fun detachSurface()
    external fun command(cmd: Array<String>)
    external fun commandAsync(cmd: Array<String>, replyUserdata: Long = 0L)
    external fun setOptionString(name: String, value: String): Int
    external fun getPropertyString(property: String): String?
    external fun setPropertyString(property: String, value: String): Int
    external fun getPropertyInt(property: String): Int?
    external fun setPropertyInt(property: String, value: Int): Int
    external fun getPropertyDouble(property: String): Double?
    external fun setPropertyDouble(property: String, value: Double): Int
    external fun getPropertyBoolean(property: String): Boolean?
    external fun setPropertyBoolean(property: String, value: Boolean): Int

    interface EventObserver {
        fun onPropertyChange(property: String, value: Any?)
        fun onPlaybackStateChange(isPlaying: Boolean)
        fun onPlaybackEnd()
        fun onFileLoaded() {}
    }

    interface LogObserver {
        fun onLogMessage(prefix: String, level: Int, text: String)
    }

    private val observers = mutableListOf<EventObserver>()
    private val logObservers = mutableListOf<LogObserver>()

    fun addObserver(observer: EventObserver) {
        synchronized(observers) {
            if (!observers.contains(observer)) {
                observers.add(observer)
            }
        }
    }

    fun removeObserver(observer: EventObserver) {
        synchronized(observers) {
            observers.remove(observer)
        }
    }

    fun addLogObserver(observer: LogObserver) {
        synchronized(logObservers) {
            if (!logObservers.contains(observer)) {
                logObservers.add(observer)
            }
        }
    }

    fun removeLogObserver(observer: LogObserver) {
        synchronized(logObservers) {
            logObservers.remove(observer)
        }
    }

    // Called from JNI
    @JvmStatic
    fun onNativeLogMessage(prefix: String, level: Int, text: String) {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return

        val obsCopy = synchronized(logObservers) { logObservers.toList() }
        for (obs in obsCopy) {
            try {
                obs.onLogMessage(prefix, level, trimmed)
            } catch (t: Throwable) {
                ZakoLog.w(TAG, "LogObserver callback error: ${t.message}")
            }
        }

        // Direct to ZakoLog based on level
        // mpv log levels: 10=fatal, 20=error, 30=warn, 40=info, 50=v, 60=debug, 70=trace
        val tag = "MPV/$prefix"

        // mediacodec-copy 初始化阶段探针日志，属于预期内正常回退，降级为 DEBUG 避免误报 ERROR
        if (prefix == "ffmpeg/video" && trimmed.contains("Both surface and native_window are NULL")) {
            ZakoLog.d(tag, trimmed)
            return
        }

        when {
            level <= 20 -> ZakoLog.e(tag, trimmed)
            level <= 30 -> ZakoLog.w(tag, trimmed)
            level <= 40 -> ZakoLog.i(tag, trimmed)
            else -> ZakoLog.d(tag, trimmed)
        }
    }

    @JvmStatic
    fun onNativePropertyChange(property: String, value: String) {
        val obsCopy = synchronized(observers) { observers.toList() }
        for (obs in obsCopy) {
            obs.onPropertyChange(property, value)
        }
    }

    @JvmStatic
    fun onNativeEvent(eventId: Int) {
        // Handle MPV_EVENT_END_FILE, MPV_EVENT_PLAYBACK_RESTART, etc.
        val obsCopy = synchronized(observers) { observers.toList() }
        when (eventId) {
            7 -> { // MPV_EVENT_END_FILE
                for (obs in obsCopy) obs.onPlaybackEnd()
            }
            8 -> { // MPV_EVENT_FILE_LOADED
                for (obs in obsCopy) obs.onFileLoaded()
            }
            12 -> { // MPV_EVENT_PAUSE
                for (obs in obsCopy) obs.onPlaybackStateChange(false)
            }
            13, 21 -> { // MPV_EVENT_UNPAUSE, MPV_EVENT_PLAYBACK_RESTART
                for (obs in obsCopy) obs.onPlaybackStateChange(true)
            }
        }
    }
}
