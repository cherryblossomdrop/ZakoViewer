package moe.zako.viewer.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import moe.zako.viewer.core.player.MPVView
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.components.player.CdnNodeSelectorDialog
import moe.zako.viewer.ui.components.player.GestureFeedbackOverlays
import moe.zako.viewer.ui.components.player.PlaybackSpeedDialog
import moe.zako.viewer.ui.components.player.PlayerBottomControls
import moe.zako.viewer.ui.components.player.PlayerBrightnessHelper
import moe.zako.viewer.ui.components.player.PlayerControlDefaults
import moe.zako.viewer.ui.components.player.PlayerEngineDialog
import moe.zako.viewer.ui.components.player.playerGestureDetector
import moe.zako.viewer.ui.components.player.PlayerHwDecDialog
import moe.zako.viewer.ui.components.player.PlayerTopBar
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsState()

    // 屏幕方向与沉浸式全屏处理
    LaunchedEffect(state.isLandscape) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            if (state.isLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }

    var isExiting by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            viewModel.onExit()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            PlayerBrightnessHelper.restoreWindowBrightness(activity)
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            onNavigateBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    DisposableEffect(Unit) {
        // 播放期间保持屏幕常亮
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            PlayerBrightnessHelper.restoreWindowBrightness(activity)
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (isExiting) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerOverlayTokens.Background)
            .playerGestureDetector(
                isLocked = state.playerState.isLocked,
                onSingleTap = { viewModel.toggleControls() },
                onDoubleTapLeft = { viewModel.onDoubleTapLeft() },
                onDoubleTapCenter = { viewModel.onDoubleTapCenter() },
                onDoubleTapRight = { viewModel.onDoubleTapRight() },
                onLongPressStart = { viewModel.onLongPressStart() },
                onLongPressEnd = { viewModel.onLongPressEnd() },
                onSeekDrag = { deltaMs -> viewModel.onSeekGesture(deltaMs) },
                onSeekDragEnd = { viewModel.onSeekGestureEnd() },
                onBrightnessDrag = { percent -> viewModel.onBrightnessGesture(percent) },
                onBrightnessDragEnd = { viewModel.onBrightnessGestureEnd() },
                onVolumeDrag = { percent -> viewModel.onVolumeGesture(percent) },
                onVolumeDragEnd = { viewModel.onVolumeGestureEnd() }
            )
    ) {
        // 1. MPV SurfaceView 核心视口（稳定树节点）
        if (!isExiting) {
            key(state.playerState.engine) {
                AndroidView(
                    factory = { ctx ->
                        MPVView(ctx).apply {
                            setZOrderMediaOverlay(true)
                            onSurfaceCreatedListener = { holder ->
                                viewModel.player.attachSurface(holder.surface, holder)
                            }
                            onSurfaceDestroyedListener = {
                                viewModel.player.detachSurface()
                            }
                            onSurfaceChangedListener = { _, width, height ->
                                viewModel.player.setSurfaceSize(width, height)
                            }
                        }
                    },
                    update = { view ->
                        view.aspectRatioMode = state.playerState.aspectRatio
                        view.videoWidth = state.playerState.videoWidth
                        view.videoHeight = state.playerState.videoHeight
                    },
                    onRelease = { view ->
                        view.visibility = android.view.View.GONE
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // 2. 动画手势 HUD 反馈浮层 (播放/暂停、快进退、亮度、音量、倍速加速)
        GestureFeedbackOverlays(
            showPlayPauseIndicator = state.showPlayPauseIndicator,
            isPlaying = state.isIndicatorPlaying,
            seekFeedbackDeltaSeconds = state.seekDeltaSeconds,
            seekTargetMs = state.seekTargetMs,
            brightnessPercent = state.brightnessPercent,
            volumePercent = state.volumePercent,
            isSpeedBoosted = state.isSpeedBoosted,
            boostSpeed = state.boostSpeed
        )

        // 3. 锁屏浮动解锁按钮 (仅在锁定时且控制显示状态下呈现，跟随倒计时自动隐藏)
        AnimatedVisibility(
            visible = state.showControls && state.playerState.isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                shape = CircleShape,
                color = PlayerOverlayTokens.HudBackground,
                modifier = Modifier
                    .padding(start = Spacing.Xl)
                    .clickable { viewModel.toggleLock() }
            ) {
                Icon(
                    imageVector = AppIcons.Lock,
                    contentDescription = "解锁屏幕",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(PlayerControlDefaults.LockContainerPadding)
                        .size(PlayerControlDefaults.LockIconSize)
                )
            }
        }

        // 4. 播放控制条浮层 (顶部操作栏、锁定按钮、底部波浪进度条与功能按钮)
        AnimatedVisibility(
            visible = state.showControls && !state.playerState.isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部控制栏 (复用 PlayerTopBar)
                PlayerTopBar(
                    title = state.video?.title ?: "",
                    onBack = handleBack,
                    aspectRatioLabel = state.playerState.aspectRatio.label,
                    onCycleAspectRatio = {
                        val nextMode = viewModel.cycleAspectRatio()
                        ZakoToast.show("画面比例: ${nextMode.label}")
                    },
                    showMoreMenu = state.showMoreMenu,
                    onShowMoreMenu = { viewModel.showMoreMenu(it) },
                    isLocalPlayback = state.isLocalPlayback,
                    showOrientationToggle = true,
                    onToggleOrientation = { viewModel.toggleOrientation() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                ) {
                    DropdownMenuItem(
                        text = { Text("加入收藏") },
                        leadingIcon = { Icon(imageVector = AppIcons.AddToFavorites, contentDescription = null) },
                        onClick = { viewModel.openAddToFavorites() }
                    )
                    DropdownMenuItem(
                        text = { Text("稍后再看") },
                        leadingIcon = { Icon(imageVector = AppIcons.WatchLater, contentDescription = null) },
                        onClick = {
                            viewModel.addToWatchLater()
                            ZakoToast.show("已加入稍后再看")
                        }
                    )
                    if (state.isLocalPlayback && onNavigateToDetail != null) {
                        DropdownMenuItem(
                            text = { Text("视频详情") },
                            leadingIcon = { Icon(imageVector = AppIcons.Info, contentDescription = null) },
                            onClick = { viewModel.onViewDetailClicked(onNavigateToDetail) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("外部播放") },
                        leadingIcon = { Icon(imageVector = AppIcons.OpenInNew, contentDescription = null) },
                        onClick = { viewModel.openWithExternalPlayer(context) }
                    )
                    DropdownMenuItem(
                        text = { Text("节点选择") },
                        leadingIcon = { Icon(imageVector = AppIcons.AltRoute, contentDescription = null) },
                        onClick = { viewModel.showSelectCdnDialog() }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("播放内核")
                                Text(
                                    text = "当前: ${state.playerState.engine.label}",
                                    fontSize = FontSizes.Badge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = { Icon(imageVector = AppIcons.Memory, contentDescription = null) },
                        onClick = {
                            viewModel.showMoreMenu(false)
                            viewModel.showEngineDialog(true)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("硬件解码")
                                Text(
                                    text = "当前: ${state.playerState.hwDecMode.label}",
                                    fontSize = FontSizes.Badge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = { Icon(imageVector = AppIcons.Hardware, contentDescription = null) },
                        onClick = {
                            viewModel.showMoreMenu(false)
                            viewModel.showHwDecDialog(true)
                        }
                    )
                }

                // 居中偏左：锁屏按钮
                Surface(
                    shape = CircleShape,
                    color = PlayerOverlayTokens.HudBackground,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Spacing.Xl)
                        .clickable { viewModel.toggleLock() }
                ) {
                    Icon(
                        imageVector = AppIcons.LockOpen,
                        contentDescription = "锁定屏幕",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(PlayerControlDefaults.LockContainerPadding)
                            .size(PlayerControlDefaults.LockIconSize)
                    )
                }

                // 底部控制条 (复用 PlayerBottomControls)
                val currentSpeed = if (state.playerState.isSpeedBoosted) state.playerState.boostSpeed else state.playerState.normalSpeed
                PlayerBottomControls(
                    progress = state.playerState.progress,
                    bufferedProgress = state.playerState.bufferedProgress,
                    isPlaying = state.playerState.isPlaying,
                    currentPositionMs = state.playerState.currentPositionMs,
                    durationMs = state.playerState.durationMs,
                    durationFormatted = state.playerState.durationFormatted,
                    onSeekTo = { viewModel.player.seekTo(it) },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    currentSpeed = currentSpeed,
                    onOpenSpeedDialog = { viewModel.showSpeedDialog(true) },
                    onToggleFullscreen = handleBack,
                    isFullscreen = true,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // CDN 节点选择弹窗
    if (state.showCdnDialog) {
        CdnNodeSelectorDialog(
            cdnNodes = state.cdnNodes,
            currentNode = state.currentCdnNode,
            isLocalPlayback = state.isLocalPlayback,
            isTesting = state.isCdnTesting,
            onSelectNode = { viewModel.selectCdnNode(it) },
            onDismiss = { viewModel.dismissCdnDialog() }
        )
    }

    // 收藏夹层级选择弹窗
    if (state.showFolderSelector) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissFolderSelector() },
            onFolderSelected = { targetId ->
                viewModel.addToFavorites(targetId)
                ZakoToast.show("已成功加入收藏夹索引")
            },
            onCreateFolder = { name, parentId ->
                viewModel.createFolder(name, parentId)
            }
        )
    }

    // 播放内核切换弹窗
    if (state.showEngineDialog) {
        PlayerEngineDialog(
            currentEngine = state.playerState.engine,
            onSelectEngine = {
                viewModel.switchEngine(it)
                ZakoToast.show("已切换为: ${it.label}")
            },
            onDismiss = { viewModel.showEngineDialog(false) }
        )
    }

    // 硬件解码设置弹窗
    if (state.showHwDecDialog) {
        PlayerHwDecDialog(
            currentMode = state.playerState.hwDecMode,
            onSelectMode = {
                viewModel.switchHwDecMode(it)
                ZakoToast.show("已切换为: ${it.label}")
            },
            onDismiss = { viewModel.showHwDecDialog(false) }
        )
    }

    // 播放速度选择弹窗
    if (state.showSpeedDialog) {
        val currentSpeed = if (state.playerState.isSpeedBoosted) state.playerState.boostSpeed else state.playerState.normalSpeed
        PlaybackSpeedDialog(
            currentSpeed = currentSpeed,
            onSpeedSelect = { speed -> viewModel.setPlaybackSpeed(speed) },
            onDismissRequest = { viewModel.showSpeedDialog(false) }
        )
    }
}
