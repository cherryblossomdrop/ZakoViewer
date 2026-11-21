package moe.zako.viewer.core.player

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import moe.zako.viewer.core.logger.ZakoLog

/**
 * SurfaceView implementation managing native surface lifecycle and player attachment.
 */
class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "MPVView"
    }

    private var isSurfaceCreated = false
    var onSurfaceCreatedListener: ((SurfaceHolder) -> Unit)? = null
    var onSurfaceDestroyedListener: ((SurfaceHolder) -> Unit)? = null
    var onSurfaceChangedListener: ((SurfaceHolder, Int, Int) -> Unit)? = null

    var videoWidth: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var videoHeight: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        // Ensure SurfaceView is placed on top of media overlay to avoid being obscured by Compose layers
        setZOrderMediaOverlay(true)
        holder.addCallback(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (parentWidth <= 0 || parentHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        var measuredWidth = parentWidth
        var measuredHeight = parentHeight

        when (aspectRatioMode) {
            AspectRatioMode.FILL -> {
                measuredWidth = parentWidth
                measuredHeight = parentHeight
            }
            AspectRatioMode.FIXED_16_9 -> {
                val targetRatio = 16f / 9f
                val parentRatio = parentWidth.toFloat() / parentHeight.toFloat()
                if (parentRatio > targetRatio) {
                    measuredHeight = parentHeight
                    measuredWidth = (parentHeight * targetRatio).toInt()
                } else {
                    measuredWidth = parentWidth
                    measuredHeight = (parentWidth / targetRatio).toInt()
                }
            }
            AspectRatioMode.FIXED_4_3 -> {
                val targetRatio = 4f / 3f
                val parentRatio = parentWidth.toFloat() / parentHeight.toFloat()
                if (parentRatio > targetRatio) {
                    measuredHeight = parentHeight
                    measuredWidth = (parentHeight * targetRatio).toInt()
                } else {
                    measuredWidth = parentWidth
                    measuredHeight = (parentWidth / targetRatio).toInt()
                }
            }
            AspectRatioMode.FIT -> {
                if (videoWidth > 0 && videoHeight > 0) {
                    val targetRatio = videoWidth.toFloat() / videoHeight.toFloat()
                    val parentRatio = parentWidth.toFloat() / parentHeight.toFloat()
                    if (parentRatio > targetRatio) {
                        measuredHeight = parentHeight
                        measuredWidth = (parentHeight * targetRatio).toInt()
                    } else {
                        measuredWidth = parentWidth
                        measuredHeight = (parentWidth / targetRatio).toInt()
                    }
                } else {
                    measuredWidth = parentWidth
                    measuredHeight = parentHeight
                }
            }
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        ZakoLog.d(TAG, "surfaceCreated: surface isValid=${holder.surface.isValid}")
        if (holder.surface.isValid) {
            isSurfaceCreated = true
            onSurfaceCreatedListener?.invoke(holder)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        ZakoLog.d(TAG, "surfaceChanged: width=$width, height=$height")
        if (width > 0 && height > 0) {
            if (MPVLib.isReady()) {
                try {
                    MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                } catch (_: Throwable) {}
            }
            onSurfaceChangedListener?.invoke(holder, width, height)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ZakoLog.d(TAG, "surfaceDestroyed: surface destroyed")
        isSurfaceCreated = false
        onSurfaceDestroyedListener?.invoke(holder)
    }
}
