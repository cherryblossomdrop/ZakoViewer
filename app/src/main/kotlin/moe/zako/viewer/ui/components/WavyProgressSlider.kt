package moe.zako.viewer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.zako.viewer.ui.theme.MotionTokens
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material Design 3 Expressive (MD3e) Animated Wavy Progress Bar with Buffer Highlighting.
 * Features an undulating fluid wave during active playback, flat line when paused/seeking,
 * buffered range visualization, preview-scrubbing isolation, and touch scrubber thumb.
 */
@Composable
fun WavyProgressSlider(
    progress: Float,
    bufferedProgress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: ((Float) -> Unit)? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    onSeek: ((Float) -> Unit)? = null,
    waveAmplitude: Dp = 3.5.dp,
    waveLength: Dp = 32.dp,
    trackThickness: Dp = 4.dp,
    thumbRadius: Dp = 6.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    bufferedColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f)
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentOnSeek by rememberUpdatedState(onSeek)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentDisplayProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)
    val targetBufferedProgress = maxOf(bufferedProgress.coerceIn(0f, 1f), currentDisplayProgress)
    val currentBufferedProgress by animateFloatAsState(
        targetValue = targetBufferedProgress,
        animationSpec = tween(durationMillis = MotionTokens.Duration.Compact, easing = LinearEasing),
        label = "bufferedProgress"
    )

    // MD3e Infinite Wave Motion
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Amplitude smoothly flattens when paused or scrubbing
    val targetAmplitude = if (isPlaying && !isDragging) waveAmplitude.value else 0f
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "waveAmplitude"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TouchTarget.Dense)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture

                    down.consume()
                    isDragging = true
                    val startProgress = (down.position.x / width).coerceIn(0f, 1f)
                    dragProgress = startProgress
                    currentOnValueChange?.invoke(startProgress)

                    var lastProgress = startProgress
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            change.consume()
                            val newProgress = (change.position.x / width).coerceIn(0f, 1f)
                            dragProgress = newProgress
                            lastProgress = newProgress
                            currentOnValueChange?.invoke(newProgress)
                        } else {
                            // 手指抬起 (Up)
                            change.consume()
                            break
                        }
                    }

                    isDragging = false
                    if (currentOnValueChangeFinished != null) {
                        currentOnValueChangeFinished?.invoke(lastProgress)
                    } else {
                        currentOnSeek?.invoke(lastProgress)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val density = this.density

            val trackPx = trackThickness.toPx()
            val thumbRadiusPx = if (isDragging) (thumbRadius.toPx() * 1.35f) else thumbRadius.toPx()
            val ampPx = animatedAmplitude * density
            val waveLengthPx = waveLength.toPx()

            val progressX = (currentDisplayProgress * width).coerceIn(0f, width)
            val bufferX = (currentBufferedProgress * width).coerceIn(0f, width)

            // 1. Draw Inactive Background Track (Remaining portion)
            val inactiveStartX = maxOf(progressX, bufferX)
            if (inactiveStartX < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(inactiveStartX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = trackPx,
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw Buffered Highlighted Track
            if (bufferX > progressX) {
                drawLine(
                    color = bufferedColor,
                    start = Offset(progressX, centerY),
                    end = Offset(bufferX, centerY),
                    strokeWidth = trackPx,
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw Active Track (Squiggly wave or smooth line)
            if (progressX > 0f) {
                if (ampPx > 0.5f) {
                    val wavePath = Path()
                    wavePath.moveTo(0f, centerY)

                    val stepPx = 3f
                    var x = 0f
                    while (x <= progressX) {
                        // Smoothly taper amplitude near start and near thumb end
                        val edgeTaper = ((progressX - x) / 24f).coerceIn(0f, 1f) * (x / 24f).coerceIn(0f, 1f)
                        val effectiveAmp = ampPx * edgeTaper
                        val y = centerY + effectiveAmp * sin((x / waveLengthPx) * 2 * PI.toFloat() - wavePhase)
                        wavePath.lineTo(x, y)
                        x += stepPx
                    }
                    wavePath.lineTo(progressX, centerY)

                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(
                            width = trackPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(progressX, centerY),
                        strokeWidth = trackPx,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 4. Draw Scrubber Thumb
            drawCircle(
                color = activeColor,
                radius = thumbRadiusPx,
                center = Offset(progressX, centerY)
            )
            // Subtle glow/ring on thumb when dragging
            if (isDragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.35f),
                    radius = thumbRadiusPx + Spacing.Xs.toPx(),
                    center = Offset(progressX, centerY)
                )
            }
        }
    }
}
