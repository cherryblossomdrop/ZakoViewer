package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionSpacing)
        ) {
            // 区块 1: 手势与倍速
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "手势与倍速",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )
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
                                text = "长按屏幕时的倍速（当前: ${state.longPressSpeed}x）",
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
            }

            // 区块 2: 内核与解码
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "内核与解码",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )
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
                                text = "系统内核省电稳定；MPV 兼容性与缓冲更强",
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
            }

            // 区块 3: 在线流媒体
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "在线流媒体",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )
                    DataSourceSettingsCard(
                        autoSelectCdn = state.autoSelectCdn,
                        defaultCdnIndex = state.defaultCdnIndex,
                        onAutoSelectCdnChange = { viewModel.setAutoSelectCdn(it) },
                        onDefaultCdnIndexChange = { viewModel.setDefaultCdnIndex(it) }
                    )
                }
            }

            // 区块 4: 外部播放器
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "外部播放器",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )
                    ExternalAppCard(
                        title = "自定义外部播放器",
                        actionIcon = AppIcons.Launch,
                        description = "指定本机第三方播放器（如 VLC、MX Player 等）",
                        selectedPackage = state.externalPlayerPkg,
                        selectedApp = selectedPlayerApp,
                        defaultFallbackIcon = AppIcons.PlayCircle,
                        onOpenPicker = { showPlayerPickerDialog = true },
                        onClearPackage = { viewModel.setExternalPlayer("") }
                    )
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
