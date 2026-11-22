package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.zako.viewer.core.storage.PublicStorageHelper
import moe.zako.viewer.ui.theme.AppIcons

/**
 * 设置主菜单页 (统一改名为“设置”)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToAppearance: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToShare: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showClearCacheConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.calculateCacheSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 分组 1: 个性化与隐私
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SettingsCategoryItem(
                        icon = AppIcons.Palette,
                        title = "外观设置",
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        onClick = onNavigateToAppearance
                    )
                    SettingsCategoryItem(
                        icon = AppIcons.Security,
                        title = "隐私设置",
                        shape = RoundedCornerShape(4.dp),
                        onClick = onNavigateToPrivacy
                    )
                    SettingsCategoryItem(
                        icon = AppIcons.Share,
                        title = "分享设置",
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        onClick = onNavigateToShare
                    )
                }
            }

            // 分组 2: 核心播放与离线功能
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SettingsCategoryItem(
                        icon = AppIcons.PlayCircle,
                        title = "播放设置",
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        onClick = onNavigateToPlayer
                    )
                    SettingsCategoryItem(
                        icon = AppIcons.Download,
                        title = "下载设置",
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        onClick = onNavigateToDownload
                    )
                }
            }

            // 分组 3: 存储与日志管理
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SettingsCategoryItem(
                        icon = AppIcons.CleaningServices,
                        title = "清理应用缓存",
                        subtitle = "图片加载、流媒体及网络临时文件",
                        trailingText = state.cacheSizeFormatted,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        onClick = { showClearCacheConfirmDialog = true }
                    )
                    SettingsCategoryItem(
                        icon = AppIcons.Terminal,
                        title = "运行日志与导出",
                        subtitle = "日志保存目录：${PublicStorageHelper.LOGS_DISPLAY_DIR}",
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        onClick = onNavigateToLogs
                    )
                }
            }
        }
    }

    if (showClearCacheConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmDialog = false },
            icon = { Icon(AppIcons.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("清理应用缓存") },
            text = {
                Text(
                    text = "确定要彻底清理图片缓存、播放器临时流媒体缓存及网络缓存吗？\n\n当前缓存大小: ${state.cacheSizeFormatted}\n\n（个人偏好设置、收藏夹与已下载的本地视频不会被删除）",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheConfirmDialog = false
                        viewModel.clearAppCache(context)
                    }
                ) {
                    Text("彻底清理")
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
