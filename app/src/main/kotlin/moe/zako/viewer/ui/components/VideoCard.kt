package moe.zako.viewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.VideoCoverTokens

@Composable
fun VideoCard(
    video: CosVideoItem,
    showVipBadge: Boolean = true,
    onClick: () -> Unit
) {
    val isCoverBlurEnabled = LocalCoverBlurEnabled.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(CornerRadius.Md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            // Thumbnail Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(VideoCoverTokens.AspectRatio)
                    .clip(RoundedCornerShape(topStart = CornerRadius.Md, topEnd = CornerRadius.Md))
                    .background(PlayerOverlayTokens.Background)
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = CoverCacheManager.getCoverModel(context, video.id, video.displayCover),
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isCoverBlurEnabled) Modifier.blur(VideoCoverTokens.BlurRadius).clipToBounds() else Modifier
                        ),
                    contentScale = ContentScale.Crop
                )

                // Bottom Gradient Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(VideoCoverTokens.ScrimHeight)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, PlayerOverlayTokens.Background.copy(alpha = 0.75f))
                            )
                        )
                )

                // VIP 抢先看矢量图标 (位于封面左下角，纯矢量图标无文字标签)
                if (showVipBadge && video.isVipEarlyAccess) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = Spacing.Compact, bottom = Spacing.Compact)
                            .clip(RoundedCornerShape(CornerRadius.Compact))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PlayerOverlayTokens.VipBadgeStart, PlayerOverlayTokens.VipBadgeEnd)
                                )
                            )
                            .padding(Spacing.Xs)
                    ) {
                        Icon(
                            imageVector = AppIcons.WorkspacePremium,
                            contentDescription = "VIP抢先看",
                            tint = PlayerOverlayTokens.VipBadgeContent,
                            modifier = Modifier.size(IconSize.Sm)
                        )
                    }
                }

                // Duration badge (Bottom-Right)
                DurationBadge(
                    duration = video.duration,
                    modifier = Modifier.padding(end = Spacing.Xs, bottom = Spacing.Xs)
                )
            }

            // Info Section
            Column(
                modifier = Modifier.padding(Spacing.Intermediate)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(Spacing.Xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "播放: ${video.displayViews}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    video.adddate?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
