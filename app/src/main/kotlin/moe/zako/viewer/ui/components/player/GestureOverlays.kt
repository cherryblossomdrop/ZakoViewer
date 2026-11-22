package moe.zako.viewer.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.Elevation
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.MotionTokens
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing

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
            enter = fadeIn(tween(MotionTokens.Duration.Compact)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(MotionTokens.Duration.Medium))
        ) {
            Surface(
                shape = CircleShape,
                color = PlayerOverlayTokens.HudBackground,
                modifier = Modifier.size(IconSize.Massive)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (isPlaying) "暂停" else "继续播放",
                        tint = Color.White,
                        modifier = Modifier.size(IconSize.Huge)
                    )
                }
            }
        }

        // 2. Double-tap Rewind / Fast Forward Indicator (-10s / +10s)
        AnimatedVisibility(
            visible = seekFeedbackDeltaSeconds != null,
            enter = fadeIn(tween(MotionTokens.Duration.Micro)),
            exit = fadeOut(tween(MotionTokens.Duration.Moderate))
        ) {
            seekFeedbackDeltaSeconds?.let { delta ->
                val isForward = delta > 0
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Xl),
                        color = PlayerOverlayTokens.HudBackground,
                        modifier = Modifier.padding(horizontal = Spacing.Massive)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md)
                        ) {
                            Icon(
                                imageVector = if (isForward) AppIcons.FastForward else AppIcons.FastRewind,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(IconSize.Lg)
                            )
                            Spacer(modifier = Modifier.width(Spacing.Compact))
                            Text(
                                text = "${if (isForward) "+" else ""}${delta}s",
                                color = Color.White,
                                fontSize = FontSizes.Body
                            )
                        }
                    }
                }
            }
        }

        // 3. Horizontal Swipe Seek HUD (Target Time + Delta)
        AnimatedVisibility(
            visible = seekTargetMs != null,
            enter = fadeIn(tween(MotionTokens.Duration.Micro)),
            exit = fadeOut(tween(MotionTokens.Duration.Moderate))
        ) {
            seekTargetMs?.let { targetMs ->
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Md),
                    color = PlayerOverlayTokens.HudBackground,
                    modifier = Modifier.padding(Spacing.Lg)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = Spacing.Xl, vertical = Spacing.Expanded)
                    ) {
                        Text(
                            text = VideoPlayerState.formatTime(targetMs),
                            color = Color.White,
                            fontSize = FontSizes.TitleMedium,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // 4. Vertical Left Swipe: Brightness Percentage HUD
        AnimatedVisibility(
            visible = brightnessPercent != null,
            enter = fadeIn(tween(MotionTokens.Duration.Micro)),
            exit = fadeOut(tween(MotionTokens.Duration.Moderate))
        ) {
            brightnessPercent?.let { percent ->
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Lg),
                    color = PlayerOverlayTokens.HudBackground,
                    modifier = Modifier.padding(start = Spacing.Xxl).align(Alignment.CenterStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Intermediate)
                    ) {
                        Icon(
                            imageVector = AppIcons.Brightness,
                            contentDescription = "亮度",
                            tint = Color.White,
                            modifier = Modifier.size(IconSize.Lg)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = FontSizes.BodyMedium
                        )
                    }
                }
            }
        }

        // 5. Vertical Right Swipe: Volume Percentage HUD
        AnimatedVisibility(
            visible = volumePercent != null,
            enter = fadeIn(tween(MotionTokens.Duration.Micro)),
            exit = fadeOut(tween(MotionTokens.Duration.Moderate))
        ) {
            volumePercent?.let { percent ->
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Lg),
                    color = PlayerOverlayTokens.HudBackground,
                    modifier = Modifier.padding(end = Spacing.Xxl).align(Alignment.CenterEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Intermediate)
                    ) {
                        Icon(
                            imageVector = if (percent > 0) AppIcons.VolumeUp else AppIcons.VolumeMute,
                            contentDescription = "音量",
                            tint = Color.White,
                            modifier = Modifier.size(IconSize.Lg)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = "$percent%",
                            color = Color.White,
                            fontSize = FontSizes.BodyMedium
                        )
                    }
                }
            }
        }

        // 6. Long Press Speed Boost HUD (e.g. 2.0x 倍速播放中)
        AnimatedVisibility(
            visible = isSpeedBoosted,
            enter = fadeIn(tween(MotionTokens.Duration.Micro)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(MotionTokens.Duration.Compact)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.XxlCompact)
        ) {
            Surface(
                shape = RoundedCornerShape(CornerRadius.MediumLarge),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = Elevation.Level3
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Sm)
                ) {
                    Icon(
                        imageVector = AppIcons.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(IconSize.Intermediate)
                    )
                    Spacer(modifier = Modifier.width(Spacing.Compact))
                    Text(
                        text = "${boostSpeed}x 倍速播放中",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = FontSizes.Label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
