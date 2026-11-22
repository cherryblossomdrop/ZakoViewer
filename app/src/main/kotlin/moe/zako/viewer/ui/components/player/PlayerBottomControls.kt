package moe.zako.viewer.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.ui.components.WavyProgressSlider
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.ZakoIcons

/**
 * 全屏播放与详情页内嵌播放通用的底部控制栏
 * 包含 MD3e 可动波浪进度条、播放/暂停、时间显示、倍速徽标及全屏切换按钮
 */
@Composable
fun PlayerBottomControls(
    progress: Float,
    bufferedProgress: Float,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    durationFormatted: String,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    currentSpeed: Float,
    onOpenSpeedDialog: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column {
            var isScrubbing by remember { mutableStateOf(false) }
            var scrubProgress by remember { mutableFloatStateOf(0f) }
            var lastSeekTriggerTime by remember { mutableLongStateOf(0L) }

            // MD3e 可动波浪进度条
            WavyProgressSlider(
                progress = if (isScrubbing) scrubProgress else progress,
                bufferedProgress = bufferedProgress,
                isPlaying = isPlaying,
                onValueChange = { ratio ->
                    isScrubbing = true
                    scrubProgress = ratio
                },
                onValueChangeFinished = { ratio ->
                    isScrubbing = false
                    if (durationMs > 0L) {
                        val now = System.currentTimeMillis()
                        if (now - lastSeekTriggerTime >= 150L) {
                            lastSeekTriggerTime = now
                            val targetMs = (ratio * durationMs).toLong().coerceIn(0L, durationMs)
                            onSeekTo(targetMs)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：播放/暂停 + 时间显示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                        )
                    }

                    Spacer(modifier = Modifier.width(PlayerControlDefaults.ControlSpacing))

                    val displayPosMs = if (isScrubbing) (scrubProgress * durationMs).toLong() else currentPositionMs
                    val displayPosFormatted = VideoPlayerState.formatTime(displayPosMs)
                    Text(
                        text = "$displayPosFormatted / $durationFormatted",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 右侧：倍速徽标 + 全屏切换
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlayerControlDefaults.ControlSpacing)
                ) {
                    IconButton(
                        onClick = onOpenSpeedDialog,
                        modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                    ) {
                        SpeedBadge(
                            speed = currentSpeed,
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) ZakoIcons.FullscreenExit else ZakoIcons.Fullscreen,
                            contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                            tint = Color.White,
                            modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                        )
                    }
                }
            }
        }
    }
}
