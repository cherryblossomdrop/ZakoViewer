package moe.zako.viewer.ui.screens.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.OverlayBlack
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(breadcrumbs) { folder ->
            val isLast = folder.id == breadcrumbs.lastOrNull()?.id
            AssistChip(
                onClick = { onFolderClick(folder) },
                label = { Text(folder.name, fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = if (folder.parentId == null) {
                    { Icon(imageVector = AppIcons.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                    modifier = Modifier.size(16.dp)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode && !isRootFolder) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Icon(
                imageVector = AppIcons.Folder,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

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
 * 收藏视频条目卡片
 */
@OptIn(ExperimentalFoundationApi::class)
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverBlurEnabled = LocalCoverBlurEnabled.current

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectToggle()
                    } else {
                        onPlayClick()
                    }
                },
                onLongClick = onMenuClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // 视频封面卡片
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clipToBounds()
            ) {
                AsyncImage(
                    model = CoverCacheManager.getCoverModel(context, item.videoId, item.coverUrl, autoFetch = true),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (coverBlurEnabled) Modifier.blur(18.dp) else Modifier)
                )

                if (item.isVip) {
                    Surface(
                        color = VipGold,
                        shape = RoundedCornerShape(bottomEnd = 4.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = AppIcons.WorkspacePremium,
                            contentDescription = "VIP",
                            tint = Color(0xFF2E1C00),
                            modifier = Modifier
                                .size(16.dp)
                                .padding(2.dp)
                        )
                    }
                }

                if (item.duration.isNotBlank()) {
                    Surface(
                        color = OverlayBlack,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = item.duration,
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 标题与信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "视频编号: ${item.videoId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (isSearchMode && parentFolderName != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "来自: $parentFolderName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 菜单触发器
            if (!isSelectionMode) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "选项",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
