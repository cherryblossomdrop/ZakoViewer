package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.core.external.AppPickerHelper
import moe.zako.viewer.ui.components.AppPickerDialog
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.Spacing

/**
 * 播放设置页面与播放控制区块
 */
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
                .padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg)
        ) {
            // 播放器长按倍速值设置
            item {
                Card(
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.Lg)) {
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
                        Spacer(modifier = Modifier.height(Spacing.Md))

                        val speeds = listOf(1.5f, 2.0f, 2.5f, 3.0f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
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
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.Lg)) {
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
                        Spacer(modifier = Modifier.height(Spacing.Intermediate))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
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

                        Spacer(modifier = Modifier.height(Spacing.Md))
                        Text(
                            text = "MPV 硬件解码模式",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(Spacing.Compact))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
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

            // 数据源与 CDN 选线设置
            item {
                DataSourceSettingsCard(
                    autoSelectCdn = state.autoSelectCdn,
                    defaultCdnIndex = state.defaultCdnIndex,
                    onAutoSelectCdnChange = { viewModel.setAutoSelectCdn(it) },
                    onDefaultCdnIndexChange = { viewModel.setDefaultCdnIndex(it) }
                )
            }

            // 自定义外部播放器
            item {
                ExternalAppCard(
                    title = "自定义外部播放器",
                    actionIcon = AppIcons.Launch,
                    description = "通过应用选择器直接从本机已安装播放器（如 VLC, MX Player, MPV 等）中选择，支持透传 HTTP Headers",
                    selectedPackage = state.externalPlayerPkg,
                    selectedApp = selectedPlayerApp,
                    defaultFallbackIcon = AppIcons.PlayCircle,
                    onOpenPicker = { showPlayerPickerDialog = true },
                    onClearPackage = { viewModel.setExternalPlayer("") }
                )
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
