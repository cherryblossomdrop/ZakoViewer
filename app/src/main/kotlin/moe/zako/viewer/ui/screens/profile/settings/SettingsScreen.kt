package moe.zako.viewer.ui.screens.profile.settings

import moe.zako.viewer.ui.components.ZakoToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import moe.zako.viewer.core.external.AppPickerHelper
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.core.storage.PublicStorageHelper
import moe.zako.viewer.ui.components.AppPickerDialog
import moe.zako.viewer.ui.components.CustomColorDialog
import moe.zako.viewer.ui.components.FolderBrowserDialog
import moe.zako.viewer.ui.theme.ThemePreset
import moe.zako.viewer.ui.theme.parseHexColor
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import java.io.File

// ==========================================
// 1. 设置主菜单页 (统一改名为“设置”)
// ==========================================
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
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                        icon = Icons.Default.Palette,
                        title = "外观设置",
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        onClick = onNavigateToAppearance
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Security,
                        title = "隐私设置",
                        shape = RoundedCornerShape(4.dp),
                        onClick = onNavigateToPrivacy
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Share,
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
                        icon = Icons.Default.PlayCircle,
                        title = "播放设置",
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        onClick = onNavigateToPlayer
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Download,
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
                        icon = Icons.Default.CleaningServices,
                        title = "清理应用缓存",
                        subtitle = "图片加载、流媒体及网络临时文件",
                        trailingText = state.cacheSizeFormatted,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        onClick = { showClearCacheConfirmDialog = true }
                    )
                    SettingsCategoryItem(
                        icon = Icons.Default.Terminal,
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
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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

@Composable
fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!trailingText.isNullOrEmpty()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ==========================================
// 2. 外观设置子页面 (莫奈动态取色 + 主题色系统)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var themeToDelete by remember { mutableStateOf<String?>(null) }

    val showRestartToast = {
        ZakoToast.show("图标设置已保存，重启应用后生效")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标颜色跟随应用主题色开关 (默认状态为关闭 false)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "图标颜色跟随应用主题色",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "应用桌面图标变体将自适应跟随莫奈动态取色或自定义主题色切换（重启应用后生效）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.appIconFollowTheme,
                            onCheckedChange = {
                                viewModel.setAppIconFollowTheme(it)
                                showRestartToast()
                            }
                        )
                    }
                }
            }

            // 莫奈取色开关
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "莫奈动态取色",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "跟随 Android 12+ 系统壁纸色彩动态生成协调的 Material 3 配色方案",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = {
                                viewModel.setDynamicColor(it)
                                if (state.appIconFollowTheme) {
                                    showRestartToast()
                                }
                            }
                        )
                    }
                }
            }

            // 主题色选择器 (当莫奈取色关闭后展示)
            item {
                AnimatedVisibility(visible = !state.dynamicColor) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "应用主题色",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "选择预置主题色或添加自定义颜色，即时生效并持久化保存：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            val presets = ThemePreset.values()

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 1. 系统预置 7 款颜色（严格二字命名，不可删除）
                                presets.forEach { theme ->
                                    val isSelected = state.customTheme.equals(theme.hexColor, ignoreCase = true) ||
                                            state.customTheme.equals(theme.key, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setCustomTheme(theme.hexColor)
                                                if (state.appIconFollowTheme) {
                                                    showRestartToast()
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(theme.primaryColor)
                                                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = theme.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = "${theme.hexColor} (内置预置)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "已选择",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. 用户保存的自定义颜色（支持删除）
                                val customList = state.savedCustomThemes.filter { !ThemePreset.isBuiltinPreset(it) }
                                customList.forEach { customHex ->
                                    val isSelected = state.customTheme.equals(customHex, ignoreCase = true)
                                    val color = parseHexColor(customHex) ?: MaterialTheme.colorScheme.primary
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setCustomTheme(customHex)
                                                if (state.appIconFollowTheme) {
                                                    showRestartToast()
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "自定义颜色",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = customHex,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "已选择",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            IconButton(
                                                onClick = { themeToDelete = customHex },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "删除该自定义颜色",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. “自定义颜色” (+) 按钮入口
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCustomColorDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "添加",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "自定义主题色",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "HEX 输入或可视化调色板选色",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initialColorHex = state.customTheme,
            onDismiss = { showCustomColorDialog = false },
            onSaveAndApply = { newHex ->
                viewModel.addCustomTheme(newHex)
                if (state.appIconFollowTheme) {
                    showRestartToast()
                }
            }
        )
    }

    if (themeToDelete != null) {
        AlertDialog(
            onDismissRequest = { themeToDelete = null },
            title = { Text("删除自定义主题色") },
            text = { Text("确定要删除自定义主题色 $themeToDelete 吗？若当前正使用该颜色，将自动回退至默认主题色。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hex = themeToDelete
                        themeToDelete = null
                        if (hex != null) {
                            viewModel.removeCustomTheme(hex)
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { themeToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

// ==========================================
// 3. 隐私设置子页面 (视频封面默认模糊)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "视频封面模糊",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "对全应用所有视频卡片与列表项（推荐页、搜索结果、历史记录、收藏夹、稍后再看、相关推荐等）的封面施加明显的隐私模糊效果，防止敏感画面社死（默认开启）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = state.coverBlurEnabled,
                            onCheckedChange = { viewModel.setCoverBlur(it) }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. 分享设置子页面 (受隐藏解锁开关控制)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsShareScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分享设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当 isLongPressShareUnlocked == false 时，界面上完全不渲染该开关
            // 当 isLongPressShareUnlocked == true 时，显示“开启长按分享复制完整链接”开关
            if (state.isLongPressShareUnlocked) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "开启长按分享复制完整链接",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "在视频详情页长按分享图标时，将直接复制视频的完整原始播放/详情链接到剪贴板",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = state.isLongPressShareEnabled,
                                onCheckedChange = { viewModel.setLongPressShareEnabled(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. 播放设置子页面 (播放内核引擎、硬件解码、倍速、外部播放器)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPlayerScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showPlayerPickerDialog by remember { mutableStateOf(false) }

    val selectedPlayerApp = remember(state.externalPlayerPkg) {
        AppPickerHelper.getAppByPackage(context, state.externalPlayerPkg)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 播放器长按倍速值设置
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "播放器长按倍速值",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "播放视频时长按屏幕触发的倍速播放比率（当前: ${state.longPressSpeed}x）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val speeds = listOf(1.5f, 2.0f, 2.5f, 3.0f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (s in speeds) {
                                FilterChip(
                                    selected = state.longPressSpeed == s,
                                    onClick = { viewModel.setSpeed(s) },
                                    label = { Text("${s}x") }
                                )
                            }
                        }
                    }
                }
            }

            // 默认播放内核引擎设置
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "播放内核引擎设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "系统内核兼容性强低发热；MPV 内核支持 NextPlayer 调优参数与激进流媒体预载",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.playerEngine == "system",
                                onClick = { viewModel.setPlayerEngine("system") },
                                label = { Text("系统内核") }
                            )
                            FilterChip(
                                selected = state.playerEngine == "mpv",
                                onClick = { viewModel.setPlayerEngine("mpv") },
                                label = { Text("MPV 内核") }
                            )
                            FilterChip(
                                selected = state.playerEngine == "auto",
                                onClick = { viewModel.setPlayerEngine("auto") },
                                label = { Text("自动选择") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "MPV 硬件解码模式",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.hwDecMode == "mediacodec",
                                onClick = { viewModel.setHwDecMode("mediacodec") },
                                label = { Text("硬解") }
                            )
                            FilterChip(
                                selected = state.hwDecMode == "mediacodec-copy",
                                onClick = { viewModel.setHwDecMode("mediacodec-copy") },
                                label = { Text("兼容硬解") }
                            )
                            FilterChip(
                                selected = state.hwDecMode == "no",
                                onClick = { viewModel.setHwDecMode("no") },
                                label = { Text("软解 (关闭)") }
                            )
                        }
                    }
                }
            }

            // 自定义外部播放器
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "自定义外部播放器",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { showPlayerPickerDialog = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Launch,
                                    contentDescription = "选择应用",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "通过应用选择器直接从本机已安装播放器（如 VLC, MX Player, MPV 等）中选择，支持透传 HTTP Headers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPlayerPickerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val iconBitmap = remember(selectedPlayerApp?.icon) {
                                    try {
                                        selectedPlayerApp?.icon?.toBitmap(96, 96)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedPlayerApp?.appName ?: if (state.externalPlayerPkg.isEmpty()) "系统默认选择器" else state.externalPlayerPkg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.externalPlayerPkg.ifEmpty { "每次唤起时由系统弹窗选择" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (state.externalPlayerPkg.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setExternalPlayer("") }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "清除")
                                    }
                                } else {
                                    TextButton(onClick = { showPlayerPickerDialog = true }) {
                                        Text("选择")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlayerPickerDialog) {
        AppPickerDialog(
            title = "选择自定义外部播放器",
            currentPackage = state.externalPlayerPkg,
            isVideoPlayerMode = true,
            onDismiss = { showPlayerPickerDialog = false },
            onAppSelected = { pkg ->
                viewModel.setExternalPlayer(pkg)
            }
        )
    }
}

// ==========================================
// 6. 下载设置子页面 (文件访问权限、目录、外部下载器)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDownloadScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()

    var hasStoragePermission by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess()) }
    var showPermissionGuideDialog by remember { mutableStateOf(false) }
    var showFolderBrowserDialog by remember { mutableStateOf(false) }
    var showDownloaderPickerDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = StoragePermissionHelper.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val selectedDownloaderApp = remember(state.externalDownloaderPkg) {
        AppPickerHelper.getAppByPackage(context, state.externalDownloaderPkg)
    }

    val defaultDownloadPath = remember {
        val emulated = File("/storage/emulated/0/Download/ZakoViewer")
        if (emulated.parentFile?.exists() == true) emulated.absolutePath
        else File(context.getExternalFilesDir(null), "downloads").absolutePath
    }

    val currentDisplayDownloadPath = state.downloadPath.ifEmpty { defaultDownloadPath }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 所有文件访问权限卡片
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasStoragePermission) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (hasStoragePermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "所有文件访问权限",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (hasStoragePermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (hasStoragePermission) "已授权" else "未授权",
                                    color = if (hasStoragePermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (hasStoragePermission) {
                                "应用已拥有全盘文件读写权限，可自由指定手机存储中的任意文件夹作为视频下载目录。"
                            } else {
                                "Android 11+ 系统需授予「所有文件访问权限」才能正常在设备存储或 SD 卡中创建和选择下载目录。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!hasStoragePermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { StoragePermissionHelper.requestAllFilesAccess(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("前往系统设置开启权限")
                            }
                        }
                    }
                }
            }

            // 2. 自定义下载目录 (内置文件夹选择器)
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "自定义下载目录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = {
                                if (hasStoragePermission) {
                                    showFolderBrowserDialog = true
                                } else {
                                    showPermissionGuideDialog = true
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "选择目录",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "离线缓存与视频切片保存的主目录。点击下方路径或右侧图标可打开内置文件夹选择器浏览、新建目录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (hasStoragePermission) {
                                        showFolderBrowserDialog = true
                                    } else {
                                        showPermissionGuideDialog = true
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = currentDisplayDownloadPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    if (hasStoragePermission) {
                                        showFolderBrowserDialog = true
                                    } else {
                                        showPermissionGuideDialog = true
                                    }
                                }) {
                                    Text("更改", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // 3. 自定义外部下载器
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "自定义外部下载器",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { showDownloaderPickerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "选择应用",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "详情页长按下载按钮可唤起所选外部下载器（如 1DM, ADM, 迅雷等）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDownloaderPickerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val iconBitmap = remember(selectedDownloaderApp?.icon) {
                                    try {
                                        selectedDownloaderApp?.icon?.toBitmap(96, 96)?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DownloadForOffline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedDownloaderApp?.appName ?: if (state.externalDownloaderPkg.isEmpty()) "系统默认选择器" else state.externalDownloaderPkg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = state.externalDownloaderPkg.ifEmpty { "每次唤起时由系统弹窗选择" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (state.externalDownloaderPkg.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setExternalDownloader("") }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "清除")
                                    }
                                } else {
                                    TextButton(onClick = { showDownloaderPickerDialog = true }) {
                                        Text("选择")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Permission guidance dialog
    if (showPermissionGuideDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionGuideDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("需要「所有文件访问权限」") },
            text = {
                Text(
                    text = "自定义下载目录需要系统授予「所有文件访问权限」，以便在手机外部存储或 SD 卡中自由读写文件。点击「前往设置」开启该权限。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionGuideDialog = false
                    StoragePermissionHelper.requestAllFilesAccess(context)
                }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionGuideDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Built-in Folder Browser Dialog
    if (showFolderBrowserDialog) {
        FolderBrowserDialog(
            initialPath = currentDisplayDownloadPath,
            onDismiss = { showFolderBrowserDialog = false },
            onFolderSelected = { selectedPath ->
                viewModel.requestChangeDownloadPath(selectedPath, defaultDownloadPath)
            }
        )
    }

    // Migration Confirmation & Progress Dialog
    if (state.showMigrationDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isMigrating) {
                    viewModel.dismissMigrationDialog()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !state.isMigrating,
                dismissOnClickOutside = !state.isMigrating
            ),
            title = { Text(if (state.isMigrating) "正在迁移文件" else "迁移已下载文件") },
            text = {
                if (state.isMigrating) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.migrationProgressText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "检测到原下载目录存在 ${state.filesToMigrateCount} 个已下载视频文件。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "是否将已下载视频完整移动至新目录，并同步更新本地记录？",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "新目录: ${state.pendingNewDownloadPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (!state.isMigrating) {
                    Button(onClick = { viewModel.confirmMigration() }) {
                        Text("迁移并更新")
                    }
                }
            },
            dismissButton = {
                if (!state.isMigrating) {
                    Row {
                        TextButton(onClick = { viewModel.skipMigrationAndSetPath() }) {
                            Text("仅更改路径")
                        }
                        TextButton(onClick = { viewModel.dismissMigrationDialog() }) {
                            Text("取消")
                        }
                    }
                }
            }
        )
    }

    // External Downloader App Picker Dialog
    if (showDownloaderPickerDialog) {
        AppPickerDialog(
            title = "选择自定义外部下载器",
            currentPackage = state.externalDownloaderPkg,
            isVideoPlayerMode = false,
            onDismiss = { showDownloaderPickerDialog = false },
            onAppSelected = { pkg ->
                viewModel.setExternalDownloader(pkg)
            }
        )
    }
}
