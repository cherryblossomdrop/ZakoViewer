package moe.zako.viewer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.VideoCoverTokens

/**
 * 全局统一横向视频列表卡片组件
 *
 * 统筹收藏夹、离线下载、历史记录及稍后再看 4 大列表的卡片展示骨架。
 * 严格通过 Design Tokens（[VideoCoverTokens], [Spacing], [CornerRadius] 等）驱动尺寸、圆角与间距。
 *
 * @param coverModel 封面图片数据源模型 (Coil Model)
 * @param title 视频主标题
 * @param modifier 外部修饰符
 * @param isSelectionMode 是否处于多选编辑模式
 * @param isSelected 是否被选中
 * @param onSelectToggle 多选勾选回调
 * @param onClick 单击回调（若处于多选模式，外部一般自动触发勾选）
 * @param onLongClick 长按回调（通常触发操作菜单抽屉）
 * @param duration 视频时长字符串（用于右下角 [DurationBadge]）
 * @param coverWidth 封面宽度，默认统一为 [VideoCoverTokens.ListCardWidth] (120.dp)
 * @param coverHeight 封面高度，默认统一为 [VideoCoverTokens.ListCardHeight] (68.dp)
 * @param coverAlpha 封面不透明度（如文件不存在时可置为 0.5f）
 * @param coverColorFilter 封面色彩滤镜（如文件不存在时去色置灰）
 * @param coverBadge 封面额外的悬浮徽标插槽（如 VIP 徽标，于左上角或右下角叠加）
 * @param titleColor 标题文字颜色
 * @param titleMaxLines 标题最大行数，默认 2
 * @param subtitle 副信息内容插槽（如下方放置“视频编号/观看时间/缓存大小”等）
 * @param actions 尾部操作按钮插槽（通常为操作菜单图标、播放图标或删除图标，多选模式下通常隐藏）
 * @param bottomContent 底部扩展内容插槽（如下载进度条与速率详情）
 * @param containerColor 自定义背景颜色（若为 null 则按默认 Token 根据选中态计算）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItemCard(
    coverModel: Any?,
    title: String,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    duration: String? = null,
    coverWidth: Dp = VideoCoverTokens.ListCardWidth,
    coverHeight: Dp = VideoCoverTokens.ListCardHeight,
    coverAlpha: Float = 1f,
    coverColorFilter: ColorFilter? = null,
    coverBadge: @Composable (BoxScope.() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleMaxLines: Int = 2,
    isLocalCached: Boolean = false,
    subtitle: @Composable (ColumnScope.() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    bottomContent: @Composable (ColumnScope.() -> Unit)? = null,
    containerColor: Color? = null
) {
    val isCoverBlurEnabled = LocalCoverBlurEnabled.current
    val cardColor = containerColor ?: if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Card(
        shape = RoundedCornerShape(CornerRadius.Md),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.Md))
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle?.invoke() }
                    )
                    Spacer(modifier = Modifier.width(Spacing.Xs))
                }

                // 统一视频封面 (120x68 dp, CornerRadius.Sm, 隐私脱敏模糊与时长角标)
                Box(
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .clip(RoundedCornerShape(CornerRadius.Sm))
                        .clipToBounds()
                ) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        colorFilter = coverColorFilter,
                        alpha = coverAlpha,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isCoverBlurEnabled) Modifier.blur(VideoCoverTokens.BlurRadius) else Modifier)
                    )

                    coverBadge?.invoke(this)
                    DurationBadge(duration = duration)
                }

                Spacer(modifier = Modifier.width(Spacing.Md))

                // 标题与副信息
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null || isLocalCached) {
                        Spacer(modifier = Modifier.height(Spacing.Xs))
                        if (subtitle != null) {
                            subtitle()
                        }
                        if (isLocalCached) {
                            if (subtitle != null) {
                                Spacer(modifier = Modifier.height(Spacing.Xxs))
                            }
                            Text(
                                text = "本地已缓存",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 尾部操作区 (多选模式下通常不展示)
                if (!isSelectionMode && actions != null) {
                    actions()
                }
            }

            // 底部扩展插槽 (如 ActiveDownloadItem 的进度指示器)
            bottomContent?.invoke(this)
        }
    }
}
