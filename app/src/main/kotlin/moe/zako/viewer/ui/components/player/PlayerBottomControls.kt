package moe.zako.viewer.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.ui.components.WavyProgressSlider
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing

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
                    colors = listOf(Color.Transparent, PlayerOverlayTokens.Background.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = Spacing.Md, vertical = Spacing.Compact)
    ) {
        Column {
            var isScrubbing by remember { mutableStateOf(false) }
            var scrubProgress by remember { mutableFloatStateOf(0f) }
            var pendingSeekRatio by remember { mutableStateOf<Float?>(null) }
            var pendingSeekTime by remember { mutableLongStateOf(0L) }

            // 寻道目标保持保护：松手后至底层引擎完成跳转期间保持落点进度，杜绝瞬态闪回旧播放位置
            if (pendingSeekRatio != null) {
                val elapsed = System.currentTimeMillis() - pendingSeekTime
                val diff = kotlin.math.abs(progress - (pendingSeekRatio ?: 0f))
                if (diff < 0.02f || elapsed > 1200L) {
                    pendingSeekRatio = null
                }
            }

            val effectiveProgress = when {
                isScrubbing -> scrubProgress
                pendingSeekRatio != null -> pendingSeekRatio!!
                else -> progress
            }

            // MD3e 可动波浪进度条
            WavyProgressSlider(
                progress = effectiveProgress,
                bufferedProgress = bufferedProgress,
                isPlaying = isPlaying,
                onValueChange = { ratio ->
                    isScrubbing = true
                    scrubProgress = ratio
                },
                onValueChangeFinished = { ratio ->
                    isScrubbing = false
                    if (durationMs > 0L) {
                        val targetMs = (ratio * durationMs).toLong().coerceIn(0L, durationMs)
                        pendingSeekRatio = ratio
                        pendingSeekTime = System.currentTimeMillis()
                        onSeekTo(targetMs)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.Xxs)
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

                    val displayPosMs = when {
                        isScrubbing -> (scrubProgress * durationMs).toLong()
                        pendingSeekRatio != null -> (pendingSeekRatio!! * durationMs).toLong()
                        else -> currentPositionMs
                    }
                    val displayPosFormatted = VideoPlayerState.formatTime(displayPosMs)
                    Text(
                        text = "$displayPosFormatted / $durationFormatted",
                        color = Color.White,
                        fontSize = FontSizes.Caption,
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
                            imageVector = if (isFullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
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
