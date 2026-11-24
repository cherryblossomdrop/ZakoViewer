package moe.zako.viewer.ui.screens.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.ui.components.VideoListItemCard
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.VipGold

/**
 * 收藏夹顶部面包屑导航路径条
 */
@Composable
fun BreadcrumbBar(
    breadcrumbs: List<FavoriteFolderEntity>,
    onFolderClick: (FavoriteFolderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (breadcrumbs.size <= 1) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
    ) {
        items(breadcrumbs) { folder ->
            val isLast = folder.id == breadcrumbs.lastOrNull()?.id
            AssistChip(
                onClick = { onFolderClick(folder) },
                label = { Text(folder.name, fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = if (folder.parentId == null) {
                    { Icon(imageVector = AppIcons.FolderSpecial, contentDescription = null, modifier = Modifier.size(IconSize.Sm)) }
                } else null,
                colors = if (isLast) {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else AssistChipDefaults.assistChipColors()
            )
            if (!isLast) {
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Sm)
                )
            }
        }
    }
}

/**
 * 收藏夹子目录条目卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteFolderItem(
    folder: FavoriteFolderEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onFolderClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRootFolder = folder.parentId == null

    Card(
        shape = RoundedCornerShape(CornerRadius.Md),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Xxs)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        if (!isRootFolder) onSelectToggle()
                    } else {
                        onFolderClick()
                    }
                },
                onLongClick = {
                    if (!isRootFolder) {
                        onMenuClick()
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Expanded, vertical = Spacing.Intermediate),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode && !isRootFolder) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = Spacing.Sm)
                )
            }

            Icon(
                imageVector = AppIcons.Folder,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(IconSize.ColorCircle)
            )

            Spacer(modifier = Modifier.width(Spacing.Md))

            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!isSelectionMode) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "菜单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 收藏视频条目卡片 (复用全局统一 VideoListItemCard)
 */
@Composable
fun FavoriteVideoItem(
    item: FavoriteItemEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isSearchMode: Boolean,
    parentFolderName: String?,
    onPlayClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onMenuClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isLocalCached: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverModel = remember(item.videoId, item.coverUrl) {
        CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true)
    }

    VideoListItemCard(
        coverModel = coverModel,
        title = item.title,
        duration = item.duration,
        isLocalCached = isLocalCached,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        onSelectToggle = onSelectToggle,
        onClick = {
            if (isSelectionMode) {
                onSelectToggle()
            } else {
                onPlayClick()
            }
        },
        onLongClick = onMenuClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Xxs),
        coverBadge = if (item.isVip) {
            {
                Surface(
                    color = VipGold,
                    shape = RoundedCornerShape(bottomEnd = CornerRadius.Xs),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = AppIcons.WorkspacePremium,
                        contentDescription = "VIP",
                        tint = PlayerOverlayTokens.VipBadgeContent,
                        modifier = Modifier
                            .size(IconSize.Sm)
                            .padding(Spacing.Xxs)
                    )
                }
            }
        } else null,
        subtitle = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "视频编号: ${item.videoId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSearchMode && parentFolderName != null) {
                    Spacer(modifier = Modifier.width(Spacing.Sm))
                    Text(
                        text = "来自: $parentFolderName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = AppIcons.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
