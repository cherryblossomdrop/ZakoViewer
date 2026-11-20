package moe.zako.viewer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

fun Modifier.playerGestureDetector(
    isLocked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    onSeekDrag: (deltaMs: Long) -> Unit,
    onSeekDragEnd: () -> Unit,
    onBrightnessDrag: (percent: Int) -> Unit,
    onBrightnessDragEnd: () -> Unit,
    onVolumeDrag: (percent: Int) -> Unit,
    onVolumeDragEnd: () -> Unit,
    currentBrightness: Float,
    currentVolume: Float,
    maxVolume: Int,
    setBrightness: (Float) -> Unit,
    setVolume: (Int) -> Unit
): Modifier {
    if (isLocked) return this

    return this.pointerInput(isLocked) {
        coroutineScope {
            var lastTapTime = 0L
            var lastTapPos = Offset.Zero
            var singleTapJob: Job? = null

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downTime = System.currentTimeMillis()
                val downPos = down.position
                val screenWidth = size.width
                val screenHeight = size.height

                var isDrag = false
                var isLongPress = false
                var dragType = 0 // 0: None, 1: Seek (horizontal), 2: Brightness (vert left), 3: Volume (vert right)
                var totalDragX = 0f
                var totalDragY = 0f

                var wasConsumed = down.isConsumed

                val initialBrightness = if (currentBrightness < 0f) 0.5f else currentBrightness
                val initialVolume = currentVolume

                // Watch for long press
                val longPressJob = launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    if (!isDrag && !wasConsumed) {
                        isLongPress = true
                        singleTapJob?.cancel()
                        lastTapTime = 0L
                        onLongPressStart()
                    }
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (change.isConsumed) {
                        wasConsumed = true
                    }

                    if (change.changedToUp()) {
                        if (change.isConsumed) {
                            wasConsumed = true
                        } else {
                            change.consume()
                        }
                        break
                    }

                    if (!change.pressed) {
                        break
                    }

                    val currentPos = change.position
                    val dragX = currentPos.x - downPos.x
                    val dragY = currentPos.y - downPos.y

                    if (!isDrag && !isLongPress && !wasConsumed) {
                        val touchSlop = viewConfiguration.touchSlop
                        if (abs(dragX) > touchSlop || abs(dragY) > touchSlop) {
                            isDrag = true
                            longPressJob.cancel()
                            singleTapJob?.cancel()
                            lastTapTime = 0L

                            if (abs(dragX) > abs(dragY)) {
                                dragType = 1 // Horizontal Seek
                            } else {
                                if (downPos.x < screenWidth * 0.5f) {
                                    dragType = 2 // Brightness (Left)
                                } else {
                                    dragType = 3 // Volume (Right)
                                }
                            }
                        }
                    }

                    if (isDrag) {
                        change.consume()
                        totalDragX = dragX
                        totalDragY = dragY

                        when (dragType) {
                            1 -> {
                                val seekDeltaMs = (totalDragX * 90).toLong()
                                onSeekDrag(seekDeltaMs)
                            }
                            2 -> {
                                val dragRange = (screenHeight * 0.7f).coerceAtLeast(100f)
                                val newB = (initialBrightness + (-totalDragY / dragRange)).coerceIn(0.01f, 1.0f)
                                setBrightness(newB)
                                onBrightnessDrag((newB * 100).toInt())
                            }
                            3 -> {
                                val dragRange = (screenHeight * 0.7f).coerceAtLeast(100f)
                                val deltaFraction = -totalDragY / dragRange
                                val newVolFloat = (initialVolume + deltaFraction * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                val newVolInt = newVolFloat.roundToInt().coerceIn(0, maxVolume)
                                setVolume(newVolInt)
                                val percent = ((newVolFloat / maxVolume) * 100).roundToInt().coerceIn(0, 100)
                                onVolumeDrag(percent)
                            }
                        }
                    }
                }

                longPressJob.cancel()

                if (wasConsumed) {
                    singleTapJob?.cancel()
                    lastTapTime = 0L
                } else if (isLongPress) {
                    onLongPressEnd()
                } else if (isDrag) {
                    when (dragType) {
                        1 -> onSeekDragEnd()
                        2 -> onBrightnessDragEnd()
                        3 -> onVolumeDragEnd()
                    }
                } else {
                    // Tap or Double Tap
                    val tapTime = System.currentTimeMillis()
                    val doubleTapTimeout = 320L
                    val distance = (downPos - lastTapPos).getDistance()

                    if (lastTapTime > 0L && (tapTime - lastTapTime) < doubleTapTimeout && distance < 120f) {
                        singleTapJob?.cancel()
                        lastTapTime = 0L
                        when {
                            downPos.x < screenWidth * 0.35f -> onDoubleTapLeft()
                            downPos.x > screenWidth * 0.65f -> onDoubleTapRight()
                            else -> onDoubleTapCenter()
                        }
                    } else {
                        lastTapTime = tapTime
                        lastTapPos = downPos
                        singleTapJob?.cancel()
                        singleTapJob = launch {
                            delay(doubleTapTimeout)
                            lastTapTime = 0L
                            onSingleTap()
                        }
                    }
                }
            }
        }
    }
}
