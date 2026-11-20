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

    init {
        try {
            System.loadLibrary("mpv")
            System.loadLibrary("player")
            isNativeLoaded = true
            ZakoLog.i(TAG, "libmpv and native player libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            ZakoLog.w(TAG, "libmpv native library not present in this runtime, using simulated/fallback bridge: ${e.message}")
        } catch (t: Throwable) {
            isNativeLoaded = false
            ZakoLog.e(TAG, "libmpv native library load error: ${t.message}", t)
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
    }

    private val observers = mutableListOf<EventObserver>()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: EventObserver) {
        observers.remove(observer)
    }

    // Called from JNI
    @JvmStatic
    fun onNativePropertyChange(property: String, value: String) {
        for (obs in observers) {
            obs.onPropertyChange(property, value)
        }
    }

    @JvmStatic
    fun onNativeEvent(eventId: Int) {
        // Handle MPV_EVENT_END_FILE, MPV_EVENT_PLAYBACK_RESTART, etc.
    }
}
