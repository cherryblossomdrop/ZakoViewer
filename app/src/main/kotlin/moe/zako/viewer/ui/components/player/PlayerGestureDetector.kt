package moe.zako.viewer.ui.components.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
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
    onVolumeDragEnd: () -> Unit
): Modifier {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTapLeft by rememberUpdatedState(onDoubleTapLeft)
    val currentOnDoubleTapCenter by rememberUpdatedState(onDoubleTapCenter)
    val currentOnDoubleTapRight by rememberUpdatedState(onDoubleTapRight)
    val currentOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnSeekDrag by rememberUpdatedState(onSeekDrag)
    val currentOnSeekDragEnd by rememberUpdatedState(onSeekDragEnd)
    val currentOnBrightnessDrag by rememberUpdatedState(onBrightnessDrag)
    val currentOnBrightnessDragEnd by rememberUpdatedState(onBrightnessDragEnd)
    val currentOnVolumeDrag by rememberUpdatedState(onVolumeDrag)
    val currentOnVolumeDragEnd by rememberUpdatedState(onVolumeDragEnd)

    return this.pointerInput(isLocked) {
        coroutineScope {
            var lastTapTime = 0L
            var lastTapPos = Offset.Zero
            var singleTapJob: Job? = null

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPos = down.position
                val screenWidth = size.width
                val screenHeight = size.height

                if (isLocked) {
                    var wasConsumed = down.isConsumed
                    var isMoved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            wasConsumed = true
                        }
                        if (change.changedToUp()) {
                            if (!change.isConsumed) {
                                change.consume()
                            }
                            break
                        }
                        if (!change.pressed) break
                        val currentPos = change.position
                        val touchSlop = viewConfiguration.touchSlop
                        if (abs(currentPos.x - downPos.x) > touchSlop || abs(currentPos.y - downPos.y) > touchSlop) {
                            isMoved = true
                        }
                    }
                    if (!wasConsumed && !isMoved) {
                        currentOnSingleTap()
                    }
                    return@awaitEachGesture
                }

                var isDrag = false
                var isLongPress = false
                var dragType = 0 // 0: None, 1: Seek (horizontal), 2: Brightness (vert left), 3: Volume (vert right)
                var wasConsumed = down.isConsumed

                // 拖拽手势内部状态：判定为 dragType 时实时读取最新真实数据源，绝不捕获过期闭包
                var startBrightness = 0.5f
                var startVolumeFloat = 0f
                var minVolume = 0
                var maxVolume = 15
                var volumeRange = 15
                var lastSetVolumeInt = -1

                // Watch for long press
                val longPressJob = launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    if (!isDrag && !wasConsumed) {
                        isLongPress = true
                        singleTapJob?.cancel()
                        lastTapTime = 0L
                        currentOnLongPressStart()
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
                                    // 实时读取最新基准亮度（优先窗口属性，未设置则读系统真实亮度）
                                    startBrightness = PlayerBrightnessHelper.getCurrentBrightness(activity)
                                } else {
                                    dragType = 3 // Volume (Right)
                                    // 实时读取系统最新媒体音量
                                    val volState = PlayerVolumeHelper.getVolumeState(audioManager)
                                    startVolumeFloat = volState.currentVolume.toFloat()
                                    minVolume = volState.minVolume
                                    maxVolume = volState.maxVolume
                                    volumeRange = volState.range
                                    lastSetVolumeInt = volState.currentVolume
                                }
                            }
                        }
                    }

                    if (isDrag) {
                        change.consume()

                        when (dragType) {
                            1 -> {
                                val seekDeltaMs = (dragX * 90).toLong()
                                currentOnSeekDrag(seekDeltaMs)
                            }
                            2 -> {
                                val dragRange = (screenHeight * 0.7f).coerceAtLeast(100f)
                                val newB = (startBrightness + (-dragY / dragRange)).coerceIn(0.01f, 1.0f)
                                PlayerBrightnessHelper.setWindowBrightness(activity, newB)
                                val percent = (newB * 100).roundToInt().coerceIn(1, 100)
                                currentOnBrightnessDrag(percent)
                            }
                            3 -> {
                                val dragRange = (screenHeight * 0.7f).coerceAtLeast(100f)
                                val deltaFraction = -dragY / dragRange
                                val targetVolFloat = (startVolumeFloat + deltaFraction * volumeRange)
                                    .coerceIn(minVolume.toFloat(), maxVolume.toFloat())
                                val targetVolInt = targetVolFloat.roundToInt().coerceIn(minVolume, maxVolume)
                                if (targetVolInt != lastSetVolumeInt) {
                                    PlayerVolumeHelper.setStreamVolume(audioManager, targetVolInt)
                                    lastSetVolumeInt = targetVolInt
                                }
                                val percent = (((targetVolFloat - minVolume) / volumeRange) * 100)
                                    .roundToInt()
                                    .coerceIn(0, 100)
                                currentOnVolumeDrag(percent)
                            }
                        }
                    }
                }

                longPressJob.cancel()

                if (wasConsumed) {
                    singleTapJob?.cancel()
                    lastTapTime = 0L
                } else if (isLongPress) {
                    currentOnLongPressEnd()
                } else if (isDrag) {
                    when (dragType) {
                        1 -> currentOnSeekDragEnd()
                        2 -> currentOnBrightnessDragEnd()
                        3 -> currentOnVolumeDragEnd()
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
                            downPos.x < screenWidth * 0.35f -> currentOnDoubleTapLeft()
                            downPos.x > screenWidth * 0.65f -> currentOnDoubleTapRight()
                            else -> currentOnDoubleTapCenter()
                        }
                    } else {
                        lastTapTime = tapTime
                        lastTapPos = downPos
                        singleTapJob?.cancel()
                        singleTapJob = launch {
                            delay(doubleTapTimeout)
                            lastTapTime = 0L
                            currentOnSingleTap()
                        }
                    }
                }
            }
        }
    }
}
