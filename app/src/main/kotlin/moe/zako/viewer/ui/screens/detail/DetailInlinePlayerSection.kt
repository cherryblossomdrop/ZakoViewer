package moe.zako.viewer.ui.screens.detail

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.player.MPVView
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.components.player.CdnNodeSelectorDialog
import moe.zako.viewer.ui.components.player.GestureFeedbackOverlays
import moe.zako.viewer.ui.components.player.PlaybackSpeedDialog
import moe.zako.viewer.ui.components.player.PlayerBottomControls
import moe.zako.viewer.ui.components.player.PlayerControlDefaults
import moe.zako.viewer.ui.components.player.PlayerEngineDialog
import moe.zako.viewer.ui.components.player.playerGestureDetector
import moe.zako.viewer.ui.components.player.PlayerHwDecDialog
import moe.zako.viewer.ui.components.player.PlayerTopBar
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget
import moe.zako.viewer.ui.theme.VideoCoverTokens

/**
 * 详情页原位内嵌播放器视口容器与轻量控制层
 * 高风险红线：严禁破坏 AndroidView 组合树稳定性（状态遮罩仅用 Box 叠加，保留 key(engine) 逻辑）
 * 业务契约约束：全屏切换通过 [onToggleFullscreen] 原位横屏展开，严格复用既有在线流与起播进度，严禁触发本地文件检测
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
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(VideoCoverTokens.AspectRatio))
            .background(PlayerOverlayTokens.Background)
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
            val isCoverBlurEnabled = LocalCoverBlurEnabled.current
            // 封面海报占位
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlayerOverlayTokens.Background)
                    .clickable {
                        if (video.allVideoLines.isEmpty() && video.allMp4Lines.isEmpty()) {
                            val tip = video.canPlayMsg?.takeIf { it.isNotBlank() } ?: "VIP 视频，暂无可用源"
                            ZakoToast.show(tip)
                        } else {
                            viewModel.startInlinePlayback()
                        }
                    }
            ) {
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

                // 顶部返回按钮
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.Sm)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PlayerOverlayTokens.HudBackground,
                        modifier = Modifier.size(TouchTarget.Dense)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(IconSize.Md)
                            )
                        }
                    }
                }

                // 播放图标背景与矢量图标
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = PlayerOverlayTokens.HudBackground,
                        modifier = Modifier.size(PlayerControlDefaults.InlinePlayButtonSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.Play,
                                contentDescription = "播放",
                                tint = Color.White,
                                modifier = Modifier.size(PlayerControlDefaults.InlinePlayIconSize)
                            )
                        }
                    }
                }

                // VIP 抢先看徽章
                if (video.isVipEarlyAccess) {
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Sm),
                        color = PlayerOverlayTokens.VipBadgeStart,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spacing.Md)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Brush.linearGradient(colors = listOf(PlayerOverlayTokens.VipBadgeStart, PlayerOverlayTokens.VipBadgeEnd)))
                                .padding(Spacing.Compact)
                        ) {
                            Icon(
                                imageVector = AppIcons.WorkspacePremium,
                                contentDescription = "VIP抢先看",
                                tint = PlayerOverlayTokens.VipBadgeContent,
                                modifier = Modifier.size(IconSize.Intermediate)
                            )
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

            // 4. 锁定状态浮动解锁按钮 (仅在锁定时且控制显示状态下呈现，跟随倒计时自动隐藏)
            AnimatedVisibility(
                visible = state.showControls && playerState.isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PlayerOverlayTokens.HudBackground,
                    modifier = Modifier
                        .padding(start = if (isFullscreen) Spacing.Xl else Spacing.Md)
                        .clickable { viewModel.toggleLock() }
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
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
                    // 顶部控制条 (复用 PlayerTopBar)
                    PlayerTopBar(
                        title = if (isFullscreen) video.title else "",
                        onBack = { if (isFullscreen) onToggleFullscreen() else onNavigateBack() },
                        aspectRatioLabel = playerState.aspectRatio.label,
                        onCycleAspectRatio = {
                            val nextMode = viewModel.cycleAspectRatio()
                            ZakoToast.show("画面比例: ${nextMode.label}")
                        },
                        showMoreMenu = state.showMoreMenu,
                        onShowMoreMenu = { viewModel.showMoreMenu(it) },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    ) {
                        DropdownMenuItem(
                            text = { Text("加入收藏") },
                            leadingIcon = { Icon(AppIcons.AddToFavorites, contentDescription = null) },
                            onClick = { viewModel.openAddToFavorites() }
                        )
                        DropdownMenuItem(
                            text = { Text("稍后再看") },
                            leadingIcon = { Icon(AppIcons.WatchLater, contentDescription = null) },
                            onClick = { viewModel.addToWatchLater(); ZakoToast.show("已加入稍后再看") }
                        )
                        DropdownMenuItem(
                            text = { Text("外部播放") },
                            leadingIcon = { Icon(AppIcons.OpenInNew, contentDescription = null) },
                            onClick = { viewModel.openWithExternalPlayer(context) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.isCdnTesting) "正在测速" else "节点测速") },
                            leadingIcon = { Icon(AppIcons.NetworkCheck, contentDescription = null) },
                            onClick = { viewModel.triggerCdnSpeedTest(); ZakoToast.show("正在测速选线...") }
                        )
                        DropdownMenuItem(
                            text = { Text("节点选择") },
                            leadingIcon = { Icon(AppIcons.AltRoute, contentDescription = null) },
                            onClick = { viewModel.showSelectCdnDialog() }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("播放内核")
                                    Text("当前: ${playerState.engine.label}", fontSize = FontSizes.Badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = { Icon(AppIcons.Memory, contentDescription = null) },
                            onClick = { viewModel.showMoreMenu(false); viewModel.showEngineDialog(true) }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("硬件解码")
                                    Text("当前: ${playerState.hwDecMode.label}", fontSize = FontSizes.Badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            leadingIcon = { Icon(AppIcons.Hardware, contentDescription = null) },
                            onClick = { viewModel.showMoreMenu(false); viewModel.showHwDecDialog(true) }
                        )
                    }

                    // 居中偏左：锁屏按钮
                    Surface(
                        shape = CircleShape,
                        color = PlayerOverlayTokens.HudBackground,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = if (isFullscreen) Spacing.Xl else Spacing.Md)
                            .clickable { viewModel.toggleLock() }
                    ) {
                        Icon(
                            imageVector = AppIcons.LockOpen,
                            contentDescription = "锁定屏幕",
                            tint = Color.White,
                            modifier = Modifier.padding(PlayerControlDefaults.LockContainerPadding).size(PlayerControlDefaults.LockIconSize)
                        )
                    }

                    // 底部控制条 (复用 PlayerBottomControls)
                    val currentSpeed = if (playerState.isSpeedBoosted) playerState.boostSpeed else playerState.normalSpeed
                    PlayerBottomControls(
                        progress = playerState.progress,
                        bufferedProgress = playerState.bufferedProgress,
                        isPlaying = playerState.isPlaying,
                        currentPositionMs = playerState.currentPositionMs,
                        durationMs = playerState.durationMs,
                        durationFormatted = playerState.durationFormatted,
                        onSeekTo = { viewModel.seekTo(it) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        currentSpeed = currentSpeed,
                        onOpenSpeedDialog = { viewModel.showSpeedDialog(true) },
                        onToggleFullscreen = onToggleFullscreen,
                        isFullscreen = isFullscreen,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    )
                }
            }
        }
    }

    // --- 播放器弹窗 (倍速、CDN节点、播放内核、硬解模式) ---
    if (state.showSpeedDialog) {
        val currentSpeed = if (playerState.isSpeedBoosted) playerState.boostSpeed else playerState.normalSpeed
        PlaybackSpeedDialog(
            currentSpeed = currentSpeed,
            onSpeedSelect = { viewModel.setPlaybackSpeed(it) },
            onDismissRequest = { viewModel.showSpeedDialog(false) }
        )
    }

    if (state.showCdnDialog) {
        CdnNodeSelectorDialog(
            cdnNodes = state.cdnNodes,
            currentNode = state.currentCdnNode,
            isLocalPlayback = false,
            isTesting = state.isCdnTesting,
            onSelectNode = { viewModel.selectCdnNode(it) },
            onDismiss = { viewModel.dismissCdnDialog() }
        )
    }

    if (state.showEngineDialog) {
        PlayerEngineDialog(
            currentEngine = playerState.engine,
            onSelectEngine = { viewModel.switchEngine(it) },
            onDismiss = { viewModel.showEngineDialog(false) }
        )
    }

    if (state.showHwDecDialog) {
        PlayerHwDecDialog(
            currentMode = playerState.hwDecMode,
            onSelectMode = { viewModel.switchHwDecMode(it) },
            onDismiss = { viewModel.showHwDecDialog(false) }
        )
    }
}
