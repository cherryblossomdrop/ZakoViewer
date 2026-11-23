package moe.zako.viewer.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.ui.screens.profile.settings.SettingsViewModel
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.ProfileTokens
import moe.zako.viewer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToWatchLater: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAppearance: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToShare: () -> Unit,
    onNavigateToSettingsDownload: () -> Unit,
    onNavigateToDataSource: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToAbout: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val settingsState by settingsViewModel.uiState.collectAsState()
    var showClearCacheConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.calculateCacheSize(context)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "我的",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionSpacing),
            contentPadding = PaddingValues(top = Spacing.Xs, bottom = Spacing.XxlCompact)
        ) {
            // 1. 顶部大卡片快捷区 (历史记录 + 离线下载)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Md)
                ) {
                    // 历史记录
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(ProfileTokens.HeroCardHeight)
                            .clickable { onNavigateToHistory() },
                        shape = RoundedCornerShape(CornerRadius.MediumLarge),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.Lg),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.LargeAction)
                                )
                                Icon(
                                    imageVector = AppIcons.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.Intermediate)
                                )
                            }
                            Column {
                                Text(
                                    text = "历史记录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(Spacing.Xxs))
                                Text(
                                    text = "查看历史记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 离线下载
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(ProfileTokens.HeroCardHeight)
                            .clickable { onNavigateToDownloads() },
                        shape = RoundedCornerShape(CornerRadius.MediumLarge),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.Lg),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.LargeAction)
                                )
                                Icon(
                                    imageVector = AppIcons.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.Intermediate)
                                )
                            }
                            Column {
                                Text(
                                    text = "离线下载",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(Spacing.Xxs))
                                Text(
                                    text = "管理离线内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 2. 分组 1: 内容与偏好 (MD3 Expressive 紧凑分组)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "内容与偏好",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )

                    ProfileItemCard(
                        icon = AppIcons.WatchLater,
                        title = "稍后再看",
                        shape = RoundedCornerShape(
                            topStart = CornerRadius.Lg,
                            topEnd = CornerRadius.Lg,
                            bottomStart = CornerRadius.Xs,
                            bottomEnd = CornerRadius.Xs
                        ),
                        onClick = onNavigateToWatchLater
                    )

                    ProfileItemCard(
                        icon = AppIcons.Palette,
                        title = "外观设置",
                        shape = RoundedCornerShape(CornerRadius.Xs),
                        onClick = onNavigateToAppearance
                    )

                    ProfileItemCard(
                        icon = AppIcons.PlayCircle,
                        title = "播放设置",
                        shape = RoundedCornerShape(CornerRadius.Xs),
                        onClick = onNavigateToPlayback
                    )

                    ProfileItemCard(
                        icon = AppIcons.Security,
                        title = "隐私设置",
                        shape = RoundedCornerShape(
                            topStart = CornerRadius.Xs,
                            topEnd = CornerRadius.Xs,
                            bottomStart = if (settingsState.hasVisibleShareSettings) CornerRadius.Xs else CornerRadius.Lg,
                            bottomEnd = if (settingsState.hasVisibleShareSettings) CornerRadius.Xs else CornerRadius.Lg
                        ),
                        onClick = onNavigateToPrivacy
                    )

                    if (settingsState.hasVisibleShareSettings) {
                        ProfileItemCard(
                            icon = AppIcons.Share,
                            title = "分享设置",
                            shape = RoundedCornerShape(
                                topStart = CornerRadius.Xs,
                                topEnd = CornerRadius.Xs,
                                bottomStart = CornerRadius.Lg,
                                bottomEnd = CornerRadius.Lg
                            ),
                            onClick = onNavigateToShare
                        )
                    }
                }
            }

            // 3. 分组 2: 数据与应用 (MD3 Expressive 紧凑分组)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "数据与应用",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )

                    ProfileItemCard(
                        icon = AppIcons.Sync,
                        title = "同步备份",
                        shape = RoundedCornerShape(
                            topStart = CornerRadius.Lg,
                            topEnd = CornerRadius.Lg,
                            bottomStart = CornerRadius.Xs,
                            bottomEnd = CornerRadius.Xs
                        ),
                        onClick = onNavigateToBackupRestore
                    )

                    ProfileItemCard(
                        icon = AppIcons.FolderOpen,
                        title = "下载设置",
                        shape = RoundedCornerShape(CornerRadius.Xs),
                        onClick = onNavigateToSettingsDownload
                    )

                    if (settingsState.hasVisibleDataSourceSettings) {
                        ProfileItemCard(
                            icon = AppIcons.AltRoute,
                            title = "源站设置",
                            shape = RoundedCornerShape(CornerRadius.Xs),
                            onClick = onNavigateToDataSource
                        )
                    }

                    ProfileItemCard(
                        icon = AppIcons.CleaningServices,
                        title = "清理缓存",
                        shape = RoundedCornerShape(CornerRadius.Xs),
                        onClick = { showClearCacheConfirmDialog = true }
                    )

                    ProfileItemCard(
                        icon = AppIcons.Info,
                        title = "关于",
                        shape = RoundedCornerShape(
                            topStart = CornerRadius.Xs,
                            topEnd = CornerRadius.Xs,
                            bottomStart = CornerRadius.Lg,
                            bottomEnd = CornerRadius.Lg
                        ),
                        onClick = onNavigateToAbout
                    )
                }
            }
        }
    }

    if (showClearCacheConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmDialog = false },
            icon = { Icon(AppIcons.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("清理缓存") },
            text = {
                Text(
                    text = "确定要彻底清理图片缓存、播放器临时流媒体缓存及网络缓存吗？\n\n当前缓存大小: ${settingsState.cacheSizeFormatted}\n\n（个人偏好设置、收藏夹与已下载的本地视频不会被删除）",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirmDialog = false
                        settingsViewModel.clearAppCache(context)
                    }
                ) {
                    Text("清理", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ProfileItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(CornerRadius.Md),
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Lg, vertical = Spacing.ListItemVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(IconSize.MediumLarge)
            )

            Spacer(modifier = Modifier.width(Spacing.Lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.Xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.Sm)
                )
            }

            Icon(
                imageVector = AppIcons.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(IconSize.Chevron)
            )
        }
    }
}
