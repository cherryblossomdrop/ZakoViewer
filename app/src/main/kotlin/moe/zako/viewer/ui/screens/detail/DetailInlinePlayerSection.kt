package moe.zako.viewer.ui.screens.detail

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.player.MPVView
import moe.zako.viewer.core.player.VideoPlayerState
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.*
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.OverlayBlack
import moe.zako.viewer.ui.theme.ZakoIcons

/**
 * 详情页原位内嵌播放器视口容器与轻量控制层
 * 高风险红线：严禁破坏 AndroidView 组合树稳定性（状态遮罩仅用 Box 叠加，保留 key(engine) 逻辑）
 */
@Composable
fun DetailEmbeddedPlayer(
    viewModel: VideoDetailViewModel,
    video: CosVideoItem,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val playerState by viewModel.player.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // 播放期间保持屏幕常亮
    DisposableEffect(state.isInlinePlaying) {
        if (state.isInlinePlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val gestureModifier = if (state.isInlinePlaying) {
        Modifier.playerGestureDetector(
            isLocked = playerState.isLocked,
            onSingleTap = { viewModel.toggleControls() },
            onDoubleTapLeft = { viewModel.onDoubleTapLeft() },
            onDoubleTapCenter = { viewModel.onDoubleTapCenter() },
            onDoubleTapRight = { viewModel.onDoubleTapRight() },
            onLongPressStart = { viewModel.onLongPressStart() },
            onLongPressEnd = { viewModel.onLongPressEnd() },
            onSeekDrag = { viewModel.onSeekGesture(it) },
            onSeekDragEnd = { viewModel.onSeekGestureEnd() },
            onBrightnessDrag = { viewModel.onBrightnessGesture(it) },
            onBrightnessDragEnd = { viewModel.onBrightnessGestureEnd() },
            onVolumeDrag = { viewModel.onVolumeGesture(it) },
            onVolumeDragEnd = { viewModel.onVolumeGestureEnd() }
        )
    } else Modifier

    Box(
        modifier = modifier
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
            .background(Color.Black)
            .then(gestureModifier)
    ) {
        // 1. MPV SurfaceView: 保持在稳定的组合树最底层节点中，绝不被条件分支卸载或替换
        key(playerState.engine) {
            AndroidView(
                factory = { ctx ->
                    MPVView(ctx).apply {
                        setZOrderMediaOverlay(true)
                        onSurfaceCreatedListener = { holder -> viewModel.player.attachSurface(holder.surface, holder) }
                        onSurfaceDestroyedListener = { viewModel.player.detachSurface() }
                        onSurfaceChangedListener = { _, width, height -> viewModel.player.setSurfaceSize(width, height) }
                    }
                },
                update = { view ->
                    view.aspectRatioMode = playerState.aspectRatio
                    view.videoWidth = playerState.videoWidth
                    view.videoHeight = playerState.videoHeight
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. 未播放封面图覆盖层 (Overlay): 纯 Box 叠加在 AndroidView 上方，点击直接起播，不卸载底层播放器
        if (!state.isInlinePlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        if (video.allVideoLines.isEmpty() && video.allMp4Lines.isEmpty()) {
                            val tip = video.canPlayMsg?.takeIf { it.isNotBlank() } ?: "VIP抢先看视频，暂无有效播放源"
                            ZakoToast.show(tip)
                        } else {
                            viewModel.startInlinePlayback()
                        }
                    }
            ) {
                val isCoverBlurEnabled = LocalCoverBlurEnabled.current
                AsyncImage(
                    model = CoverCacheManager.getCoverModel(LocalContext.current, video.id, video.displayCover),
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier),
                    contentScale = ContentScale.Crop
                )

                // 顶部返回按钮
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.ArrowBack, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // 播放图标背景与矢量图标
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.65f), modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Play, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }
                }

                // VIP 抢先看徽章
                if (video.isVipEarlyAccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFD54F),
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Brush.linearGradient(colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))))
                                .padding(6.dp)
                        ) {
                            Icon(AppIcons.WorkspacePremium, contentDescription = "VIP抢先看", tint = Color(0xFF2E1C00), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else {
            // 2. 缓冲指示器
            if (playerState.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }

            // 3. 手势 HUD 反馈浮层 (播放/暂停、快进退、亮度、音量、倍速加速)
            GestureFeedbackOverlays(
                showPlayPauseIndicator = state.showPlayPauseIndicator, isPlaying = state.isIndicatorPlaying,
                seekFeedbackDeltaSeconds = state.seekDeltaSeconds, seekTargetMs = state.seekTargetMs,
                brightnessPercent = state.brightnessPercent, volumePercent = state.volumePercent,
                isSpeedBoosted = state.isSpeedBoosted, boostSpeed = state.boostSpeed
            )

            // 4. 锁定状态浮动解锁按钮
            if (playerState.isLocked) {
                Surface(
                    shape = CircleShape,
                    color = OverlayBlack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = if (isFullscreen) 24.dp else 12.dp)
                        .clickable { viewModel.toggleLock() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "解锁屏幕",
                        tint = Color.White,
                        modifier = Modifier.padding(PlayerControlDefaults.LockContainerPadding).size(PlayerControlDefaults.LockIconSize)
                    )
                }
            }

            // 5. 播放控制条浮层 (顶部操作栏、锁定按钮、底部波浪进度条与功能按钮)
            AnimatedVisibility(
                visible = state.showControls && !playerState.isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 顶部控制条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (isFullscreen) onToggleFullscreen() else onNavigateBack() },
                                modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                            ) {
                                Icon(AppIcons.ArrowBack, contentDescription = if (isFullscreen) "退出全屏" else "返回", tint = Color.White, modifier = Modifier.size(PlayerControlDefaults.ControlIconSize))
                            }

                            if (isFullscreen) {
                                Text(
                                    text = video.title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = PlayerControlDefaults.ControlSpacing)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            // 画面比例切换 (适应屏幕、填充、16:9、4:3)
                            IconButton(
                                onClick = {
                                    val nextMode = viewModel.cycleAspectRatio()
                                    ZakoToast.show("画面比例: ${nextMode.label}")
                                },
                                modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                            ) {
                                Icon(ZakoIcons.AspectRatio, contentDescription = "画面比例 (${playerState.aspectRatio.label})", tint = Color.White, modifier = Modifier.size(PlayerControlDefaults.ControlIconSize))
                            }

                            // 更多选项菜单 (⋮)
                            Box {
                                IconButton(
                                    onClick = { viewModel.showMoreMenu(true) },
                                    modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                                ) {
                                    Icon(AppIcons.MoreVert, contentDescription = "更多选项", tint = Color.White, modifier = Modifier.size(PlayerControlDefaults.ControlIconSize))
                                }

                                DropdownMenu(
                                    expanded = state.showMoreMenu,
                                    onDismissRequest = { viewModel.showMoreMenu(false) }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("添加到收藏夹") },
                                        leadingIcon = { Icon(AppIcons.AddToFavorites, contentDescription = null) },
                                        onClick = { viewModel.openAddToFavorites() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("添加到稍后再看") },
                                        leadingIcon = { Icon(AppIcons.WatchLater, contentDescription = null) },
                                        onClick = { viewModel.addToWatchLater(); ZakoToast.show("已加入稍后再看") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("使用外部播放器打开") },
                                        leadingIcon = { Icon(AppIcons.OpenInNew, contentDescription = null) },
                                        onClick = { viewModel.openWithExternalPlayer(context) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (state.isCdnTesting) "测速中..." else "对 CDN 节点进行测速") },
                                        leadingIcon = { Icon(Icons.Rounded.NetworkCheck, contentDescription = null) },
                                        onClick = { viewModel.triggerCdnSpeedTest(); ZakoToast.show("正在并发测试全部 CDN 节点速率与延迟") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("选择 CDN 节点播放") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null) },
                                        onClick = { viewModel.showSelectCdnDialog() }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("播放内核切换")
                                                Text("当前: ${playerState.engine.label}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) },
                                        onClick = { viewModel.showMoreMenu(false); viewModel.showEngineDialog(true) }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("硬件解码设置")
                                                Text("当前: ${playerState.hwDecMode.label}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Hardware, contentDescription = null) },
                                        onClick = { viewModel.showMoreMenu(false); viewModel.showHwDecDialog(true) }
                                    )
                                }
                            }
                        }
                    }

                    // 居中偏左：锁屏按钮
                    Surface(
                        shape = CircleShape,
                        color = OverlayBlack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = if (isFullscreen) 24.dp else 12.dp)
                            .clickable { viewModel.toggleLock() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "锁定屏幕",
                            tint = Color.White,
                            modifier = Modifier.padding(PlayerControlDefaults.LockContainerPadding).size(PlayerControlDefaults.LockIconSize)
                        )
                    }

                    // 底部控制条 (波浪进度条、播放/暂停、时间、倍速、全屏按钮)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                            .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column {
                            var isScrubbing by remember { mutableStateOf(false) }
                            var scrubProgress by remember { mutableFloatStateOf(0f) }
                            var lastSeekTriggerTime by remember { mutableLongStateOf(0L) }

                            // MD3e 可动波浪进度条
                            WavyProgressSlider(
                                progress = if (isScrubbing) scrubProgress else playerState.progress,
                                bufferedProgress = playerState.bufferedProgress,
                                isPlaying = playerState.isPlaying,
                                onValueChange = { ratio -> isScrubbing = true; scrubProgress = ratio },
                                onValueChangeFinished = { ratio ->
                                    isScrubbing = false
                                    val now = System.currentTimeMillis()
                                    if (now - lastSeekTriggerTime >= 150L) {
                                        lastSeekTriggerTime = now
                                        viewModel.seekTo((ratio * playerState.durationMs).toLong())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.togglePlayPause() },
                                        modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                                    ) {
                                        Icon(
                                            imageVector = if (playerState.isPlaying) AppIcons.Pause else AppIcons.Play,
                                            contentDescription = "播放/暂停",
                                            tint = Color.White,
                                            modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(PlayerControlDefaults.ControlSpacing))

                                    val displayPosMs = if (isScrubbing) (scrubProgress * playerState.durationMs).toLong() else playerState.currentPositionMs
                                    val displayPosFormatted = VideoPlayerState.formatTime(displayPosMs)
                                    Text(
                                        text = "$displayPosFormatted / ${playerState.durationFormatted}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(PlayerControlDefaults.ControlSpacing)
                                ) {
                                    val currentSpeed = if (playerState.isSpeedBoosted) playerState.boostSpeed else playerState.normalSpeed
                                    IconButton(
                                        onClick = { viewModel.showSpeedDialog(true) },
                                        modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                                    ) {
                                        SpeedBadge(speed = currentSpeed, tint = Color.White)
                                    }

                                    IconButton(
                                        onClick = onToggleFullscreen,
                                        modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                                    ) {
                                        Icon(
                                            imageVector = if (isFullscreen) ZakoIcons.FullscreenExit else ZakoIcons.Fullscreen,
                                            contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                                            tint = Color.White,
                                            modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
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

    // --- 播放器弹窗 (倍速、CDN节点、播放内核、硬解模式) ---
    if (state.showSpeedDialog) {
        val currentSpeed = if (playerState.isSpeedBoosted) playerState.boostSpeed else playerState.normalSpeed
        PlaybackSpeedDialog(currentSpeed = currentSpeed, onSpeedSelect = { viewModel.setPlaybackSpeed(it) }, onDismissRequest = { viewModel.showSpeedDialog(false) })
    }

    if (state.showCdnDialog) {
        DetailCdnDialog(state = state, onSelectNode = { viewModel.selectCdnNode(it) }, onDismiss = { viewModel.dismissCdnDialog() })
    }

    if (state.showEngineDialog) {
        DetailEngineDialog(currentEngine = playerState.engine, onSelectEngine = { viewModel.switchEngine(it) }, onDismiss = { viewModel.showEngineDialog(false) })
    }

    if (state.showHwDecDialog) {
        DetailHwDecDialog(currentMode = playerState.hwDecMode, onSelectMode = { viewModel.switchHwDecMode(it) }, onDismiss = { viewModel.showHwDecDialog(false) })
    }
}
