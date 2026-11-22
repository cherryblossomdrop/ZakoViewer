package moe.zako.viewer.ui.components.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.ui.theme.OverlayBlack

@Composable
fun GestureFeedbackOverlays(
    showPlayPauseIndicator: Boolean,
    isPlaying: Boolean,
    seekFeedbackDeltaSeconds: Int?,
    seekTargetMs: Long?,
    brightnessPercent: Int?,
    volumePercent: Int?,
    isSpeedBoosted: Boolean,
    boostSpeed: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1. Play / Pause Central Indicator
        AnimatedVisibility(
            visible = showPlayPauseIndicator,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(250))
        ) {
            Surface(
                shape = CircleShape,
                color = OverlayBlack,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "继续播放",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // 2. Double-tap Rewind / Fast Forward Indicator (-10s / +10s)
        AnimatedVisibility(
            visible = seekFeedbackDeltaSeconds != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200))
        ) {
            seekFeedbackDeltaSeconds?.let { delta ->
                val isForward = delta > 0
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = OverlayBlack,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${if (isForward) "+" else ""}${delta}s",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Horizontal Swipe Seek HUD (Target Time + Delta)
        AnimatedVisibility(
            visible = seekTargetMs != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200))
        ) {
            seekTargetMs?.let { targetMs ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OverlayBlack,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = VideoPlayerState.formatTime(targetMs),
                            color = Color.White,
                            fontSize = 20.sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // 4. Vertical Left Swipe: Brightness Percentage HUD
        AnimatedVisibility(
            visible = brightnessPercent != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200))
        ) {
            brightnessPercent?.let { percent ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = OverlayBlack,
                    modifier = Modifier.padding(start = 32.dp).align(Alignment.CenterStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "亮度",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // 5. Vertical Right Swipe: Volume Percentage HUD
        AnimatedVisibility(
            visible = volumePercent != null,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200))
        ) {
            volumePercent?.let { percent ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = OverlayBlack,
                    modifier = Modifier.padding(end = 32.dp).align(Alignment.CenterEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (percent > 0) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "音量",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // 6. Long Press Speed Boost HUD (e.g. 2.0x 倍速播放中)
        AnimatedVisibility(
            visible = isSpeedBoosted,
            enter = fadeIn(tween(100)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${boostSpeed}x 倍速播放中",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
