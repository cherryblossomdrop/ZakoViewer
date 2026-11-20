package moe.zako.viewer.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import moe.zako.viewer.ui.components.ZakoToast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.core.player.HwDecMode
import moe.zako.viewer.core.player.MPVView
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.GestureFeedbackOverlays
import moe.zako.viewer.ui.components.SpeedBadge
import moe.zako.viewer.ui.components.WavyProgressSlider
import moe.zako.viewer.ui.components.playerGestureDetector
import moe.zako.viewer.ui.theme.ZakoIcons
import moe.zako.viewer.ui.theme.OverlayBlack
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Screen Orientation and Immersive Fullscreen handling
    LaunchedEffect(state.isLandscape) {
        val window = activity?.window
        if (window != null && activity != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (state.isLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var isExiting by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (!isExiting) {
            isExiting = true
            try {
                viewModel.player.pause()
                viewModel.player.detachSurface()
            } catch (_: Exception) {}
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            onNavigateBack()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        handleBack()
    }

    DisposableEffect(Unit) {
        // Keep screen on during playback
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
            .background(Color.Black)
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
                onVolumeDragEnd = { viewModel.onVolumeGestureEnd() },
                currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f,
                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat(),
                maxVolume = maxVolume,
                setBrightness = { newB ->
                    activity?.let { act ->
                        val lp = act.window.attributes
                        lp.screenBrightness = newB
                        act.window.attributes = lp
                    }
                },
                setVolume = { newVol ->
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                }
            )
    ) {
        // 1. MPV SurfaceView Core
        if (!isExiting) {
            AndroidView(
                factory = { ctx ->
                    MPVView(ctx).apply {
                        setZOrderMediaOverlay(true)
                        onSurfaceCreatedListener = { holder ->
                            viewModel.player.attachSurface(holder.surface)
                        }
                        onSurfaceDestroyedListener = {
                            viewModel.player.detachSurface()
                        }
                        if (holder.surface.isValid) {
                            viewModel.player.attachSurface(holder.surface)
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
                    viewModel.player.detachSurface()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 2. Visual Animated Gesture Feedback HUD Overlays
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

        // 3. Floating Unlock Button (Visible only when locked)
        if (state.playerState.isLocked) {
            Surface(
                shape = CircleShape,
                color = OverlayBlack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .clickable { viewModel.toggleLock() }
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "解锁屏幕",
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp).size(28.dp)
                )
            }
        }

        // 4. Player Controls Overlay (Top bar, center, bottom bar)
        AnimatedVisibility(
            visible = state.showControls && !state.playerState.isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Control Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = handleBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }

                        Text(
                            text = state.video?.title ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )

                        // 画面比例切换 (适应屏幕、填充、16:9、4:3) - 方正圆角画框矢量图标
                        IconButton(onClick = {
                            val nextMode = viewModel.cycleAspectRatio()
                            ZakoToast.show("画面比例: ${nextMode.label}")
                        }) {
                            Icon(
                                imageVector = ZakoIcons.AspectRatio,
                                contentDescription = "画面比例 (${state.playerState.aspectRatio.label})",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 屏幕方向旋转 (横竖屏切换)
                        IconButton(onClick = { viewModel.toggleOrientation() }) {
                            Icon(imageVector = Icons.Default.ScreenRotation, contentDescription = "旋转屏幕", tint = Color.White)
                        }

                        // “更多选项” 菜单 (⋮)
                        Box {
                            IconButton(onClick = { viewModel.showMoreMenu(true) }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "更多选项", tint = Color.White)
                            }

                            // 5 项功能菜单
                            DropdownMenu(
                                expanded = state.showMoreMenu,
                                onDismissRequest = { viewModel.showMoreMenu(false) }
                            ) {
                                // 1. 添加到收藏夹
                                DropdownMenuItem(
                                    text = { Text("添加到收藏夹") },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Favorite, contentDescription = null) },
                                    onClick = { viewModel.openAddToFavorites() }
                                )

                                // 2. 添加到稍后再看
                                DropdownMenuItem(
                                    text = { Text("添加到稍后再看") },
                                    leadingIcon = { Icon(imageVector = ZakoIcons.WatchLater, contentDescription = null) },
                                    onClick = {
                                        viewModel.addToWatchLater()
                                        ZakoToast.show("已加入稍后再看")
                                    }
                                )

                                // 3. 使用外部播放器打开
                                DropdownMenuItem(
                                    text = { Text("使用外部播放器打开") },
                                    leadingIcon = { Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null) },
                                    onClick = { viewModel.openWithExternalPlayer(context) }
                                )

                                // 4. 对 CDN 节点进行测速
                                DropdownMenuItem(
                                    text = { Text(if (state.isCdnTesting) "测速中..." else "对 CDN 节点进行测速") },
                                    leadingIcon = { Icon(imageVector = Icons.Rounded.NetworkCheck, contentDescription = null) },
                                    onClick = {
                                        viewModel.triggerCdnSpeedTest()
                                        ZakoToast.show("正在并发测试全部 CDN 节点速率与延迟")
                                    }
                                )

                                // 5. 选择 CDN 节点播放
                                DropdownMenuItem(
                                    text = { Text("选择 CDN 节点播放") },
                                    leadingIcon = { Icon(imageVector = Icons.Default.AltRoute, contentDescription = null) },
                                    onClick = { viewModel.showSelectCdnDialog() }
                                )

                                // 6. 切换播放内核 (系统 / MPV)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("播放内核切换")
                                            Text(
                                                text = "当前: ${state.playerState.engine.label}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Memory, contentDescription = null) },
                                    onClick = {
                                        viewModel.showMoreMenu(false)
                                        viewModel.showEngineDialog(true)
                                    }
                                )

                                // 7. 硬件解码设置 (MPV / 系统)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("硬件解码设置")
                                            Text(
                                                text = "当前: ${state.playerState.hwDecMode.label}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Hardware, contentDescription = null) },
                                    onClick = {
                                        viewModel.showMoreMenu(false)
                                        viewModel.showHwDecDialog(true)
                                    }
                                )
                            }
                        }
                    }
                }

                // Center: Lock Button
                Surface(
                    shape = CircleShape,
                    color = OverlayBlack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                        .clickable { viewModel.toggleLock() }
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "锁定屏幕",
                        tint = Color.White,
                        modifier = Modifier.padding(14.dp).size(28.dp)
                    )
                }

                // Bottom Control Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        var isScrubbing by remember { mutableStateOf(false) }
                        var scrubProgress by remember { mutableFloatStateOf(0f) }
                        var lastSeekTriggerTime by remember { mutableLongStateOf(0L) }

                        // MD3e 可动波浪进度条 (遵循 Material Design 3 Expressive 规范，含高亮缓冲指示)
                        WavyProgressSlider(
                            progress = if (isScrubbing) scrubProgress else state.playerState.progress,
                            bufferedProgress = state.playerState.bufferedProgress,
                            isPlaying = state.playerState.isPlaying,
                            onValueChange = { percent ->
                                isScrubbing = true
                                scrubProgress = percent
                            },
                            onValueChangeFinished = { percent ->
                                isScrubbing = false
                                val now = System.currentTimeMillis()
                                if (now - lastSeekTriggerTime >= 150L) {
                                    lastSeekTriggerTime = now
                                    val targetMs = (percent * state.playerState.durationMs).toLong()
                                    viewModel.player.seekTo(targetMs)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.togglePlayPause() }) {
                                    Icon(
                                        imageVector = if (state.playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "播放/暂停",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                val displayPosMs = if (isScrubbing) (scrubProgress * state.playerState.durationMs).toLong() else state.playerState.currentPositionMs
                                val displayPosFormatted = moe.zako.viewer.core.player.VideoPlayerState.formatTime(displayPosMs)
                                Text(
                                    text = "$displayPosFormatted / ${state.playerState.durationFormatted}",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 播放倍速切换按钮 - 动态倍速矢量徽标
                                val currentSpeed = if (state.playerState.isSpeedBoosted) state.playerState.boostSpeed else state.playerState.normalSpeed
                                IconButton(onClick = { viewModel.showSpeedDialog(true) }) {
                                    SpeedBadge(
                                        speed = currentSpeed,
                                        tint = Color.White
                                    )
                                }

                                // 退出全屏按钮（控制栏右下角退出全屏入口）
                                IconButton(onClick = handleBack) {
                                    Icon(
                                        imageVector = ZakoIcons.FullscreenExit,
                                        contentDescription = "退出全屏",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet / Dialog: 选择 CDN 节点播放 (若已执行测速，实时标注对应结果)
    if (state.showCdnDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCdnDialog() },
            title = { Text("选择 CDN 播放节点") },
            text = {
                Column {
                    Text(
                        text = "选择最流畅的视频流媒体镜像节点（已实时标注测速结果）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(state.cdnNodes) { node ->
                            val isCurrent = node.url == state.currentCdnNode?.url
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(node.name, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("(当前)", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        }
                                    }
                                },
                                supportingContent = {
                                    // 实时标注测速结果 (如延迟 / 下载速率)
                                    Text(
                                        text = "测速指标: ${node.displayMetric}",
                                        color = if (node.testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.clickable { viewModel.selectCdnNode(node) }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCdnDialog() }) {
                    Text("关闭")
                }
            }
        )
    }

    // Favorite Folder Selection Dialog
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

    // 播放内核切换弹窗 (系统 / MPV)
    if (state.showEngineDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showEngineDialog(false) },
            title = { Text("选择播放引擎内核") },
            text = {
                Column {
                    Text(
                        text = "系统内核基于 Android 原生 MediaPlayer，能耗低、稳定性高；MPV 内核支持流媒体激进缓冲、NextPlayer 调优参数及硬件解码切换：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PlayerEngine.values().forEach { engine ->
                        val isSelected = state.playerState.engine == engine
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.switchEngine(engine)
                                    ZakoToast.show("已切换为: ${engine.label}")
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.switchEngine(engine)
                                    ZakoToast.show("已切换为: ${engine.label}")
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = engine.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showEngineDialog(false) }) {
                    Text("完成")
                }
            }
        )
    }

    // 硬件解码设置弹窗 (NextPlayer 规范)
    if (state.showHwDecDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showHwDecDialog(false) },
            title = { Text("硬件解码设置 (NextPlayer 规范)") },
            text = {
                Column {
                    Text(
                        text = "选择视频硬件解码模式，建议开启以降低发热与提高播放流畅度：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HwDecMode.values().forEach { mode ->
                        val isSelected = state.playerState.hwDecMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.switchHwDecMode(mode)
                                    ZakoToast.show("已切换为: ${mode.label}")
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.switchHwDecMode(mode)
                                    ZakoToast.show("已切换为: ${mode.label}")
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showHwDecDialog(false) }) {
                    Text("完成")
                }
            }
        )
    }

    // 播放速度选择弹窗
    if (state.showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showSpeedDialog(false) },
            title = { Text("选择播放速度") },
            text = {
                Column {
                    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
                    val rows = speedOptions.chunked(4)
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (spd in row) {
                                val isSelected = state.playerState.normalSpeed == spd
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setPlaybackSpeed(spd) },
                                    label = { Text("${spd}x") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showSpeedDialog(false) }) {
                    Text("完成")
                }
            }
        )
    }
}
