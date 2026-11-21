package moe.zako.viewer.ui.screens.detail

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.core.cdn.CdnNode
import moe.zako.viewer.core.player.HwDecMode
import moe.zako.viewer.core.player.MPVView
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.core.share.ShareCipherUtil
import moe.zako.viewer.data.source.cosav.CosComment
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.GestureFeedbackOverlays
import moe.zako.viewer.ui.components.PlayerControlDefaults
import moe.zako.viewer.ui.components.SpeedBadge
import moe.zako.viewer.ui.components.VideoCard
import moe.zako.viewer.ui.components.WavyProgressSlider
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.components.playerGestureDetector
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.ZakoIcons
import moe.zako.viewer.ui.theme.OverlayBlack
import moe.zako.viewer.ui.theme.VipGold
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (videoId: String) -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit,
    onNavigateToSearch: (query: String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsState()
    val video = state.video
    val configuration = LocalConfiguration.current

    val isTabletLandscape = configuration.smallestScreenWidthDp >= 600 &&
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Screen orientation & immersive mode handling for fullscreen mode
    LaunchedEffect(state.isFullscreen) {
        val window = activity?.window
        if (window != null && activity != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (state.isFullscreen) {
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                val isPortraitVideo = state.playerState.videoWidth > 0 && (state.playerState.videoHeight > state.playerState.videoWidth)
                if (isPortraitVideo) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Intercept back button when in fullscreen
    BackHandler(enabled = state.isFullscreen) {
        viewModel.setFullscreen(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                viewModel.player.pause()
                viewModel.player.detachSurface()
            } catch (_: Exception) {}
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (video == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.errorMessage ?: "视频信息加载失败"
                )
            }
        } else {
            if (state.isFullscreen) {
                BackHandler {
                    viewModel.setFullscreen(false)
                }
            }
            val relatedList = video.relatedVideos ?: emptyList()

            if (isTabletLandscape) {
                if (state.isFullscreen) {
                    DetailEmbeddedPlayer(
                        viewModel = viewModel,
                        video = video,
                        isFullscreen = true,
                        onToggleFullscreen = { viewModel.setFullscreen(false) },
                        onNavigateBack = onNavigateBack,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 平板横屏左右双栏自适应排版 (左栏 58%, 右栏 42%)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                    // 左栏 (58% 宽度): 16:9 内嵌播放器 + 标题、番号、可点击复制元数据 + 留言区
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DetailEmbeddedPlayer(
                            viewModel = viewModel,
                            video = video,
                            isFullscreen = false,
                            onToggleFullscreen = { viewModel.setFullscreen(true) },
                            onNavigateBack = onNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        )

                        DetailHeader(
                            video = video,
                            context = context
                        )

                        // 收藏、下载、稍后再看、分享四个核心操作按钮置于视频播放卡片与标题下方
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            DetailActionButtons(
                                viewModel = viewModel,
                                state = state,
                                video = video,
                                context = context
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        DetailMetadataCard(
                            video = video,
                            context = context,
                            onNavigateToSearch = onNavigateToSearch
                        )

                        DetailCommentsCard(
                            video = video,
                            isExpanded = state.isCommentsExpanded,
                            onToggleExpand = { viewModel.toggleCommentsExpanded() }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    VerticalDivider(modifier = Modifier.fillMaxHeight())

                    // 右栏 (42% 宽度): 独立可滚动的“相关视频”
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "相关视频 (${relatedList.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 下方独立滚动的“相关视频”双列卡片列表
                        if (relatedList.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(relatedList, key = { it.id }) { relItem ->
                                    VideoCard(
                                        video = relItem,
                                        showVipBadge = true,
                                        onClick = { onNavigateToDetail(relItem.id) }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无相关推荐视频",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
                // 手机端与竖屏模式：
                // 保持播放器在单一稳定的组合节点中（避免在 LazyColumn 滚动复用或全屏切换时被销毁并触发 surfaceDestroyed）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (!state.isFullscreen) Modifier.statusBarsPadding().navigationBarsPadding()
                            else Modifier
                        )
                ) {
                    DetailEmbeddedPlayer(
                        viewModel = viewModel,
                        video = video,
                        isFullscreen = state.isFullscreen,
                        onToggleFullscreen = { viewModel.setFullscreen(!state.isFullscreen) },
                        onNavigateBack = {
                            if (state.isFullscreen) {
                                viewModel.setFullscreen(false)
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = if (state.isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                    )

                    if (!state.isFullscreen) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {

                    // 2. 标题与信息头部 (包裹 SelectionContainer 支持复制)
                    item {
                        DetailHeader(
                            video = video,
                            context = context
                        )
                    }

                    // 3. 核心操作栏 (收藏、下载、稍后再看、分享)
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            DetailActionButtons(
                                viewModel = viewModel,
                                state = state,
                                video = video,
                                context = context
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 4. 元数据展示 (上架日期、登场人物、作品、片商、标签)
                    item {
                        DetailMetadataCard(
                            video = video,
                            context = context,
                            onNavigateToSearch = onNavigateToSearch
                        )
                    }

                    // 5. 留言区 (默认折叠)
                    item {
                        DetailCommentsCard(
                            video = video,
                            isExpanded = state.isCommentsExpanded,
                            onToggleExpand = { viewModel.toggleCommentsExpanded() }
                        )
                    }

                    // 6. 扩容后的相关视频列表 (上下滑动查看更多，双列网格)
                    if (relatedList.isNotEmpty()) {
                        item {
                            Text(
                                text = "相关视频 (${relatedList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                            )
                        }

                        val pairs = relatedList.chunked(2)
                        items(pairs, key = { it.joinToString("_") { v -> v.id } }) { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    VideoCard(
                                        video = pair[0],
                                        showVipBadge = true,
                                        onClick = { onNavigateToDetail(pair[0].id) }
                                    )
                                }
                                if (pair.size > 1) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        VideoCard(
                                            video = pair[1],
                                            showVipBadge = true,
                                            onClick = { onNavigateToDetail(pair[1].id) }
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
    }
}

    // 收藏夹层级树选择弹窗
    if (state.showFolderSelector) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissFolderPicker() },
            onFolderSelected = { folderId ->
                viewModel.addToFavoriteFolder(folderId)
                ZakoToast.show("已成功加入收藏夹索引")
            },
            onCreateFolder = { name, parentId ->
                viewModel.createFolder(name, parentId)
            }
        )
    }

    // 长按下载方式弹窗 (内置下载器 vs 外部下载器)
    if (state.showDownloadChoiceDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadChoice() },
            title = { Text("选择下载方式") },
            text = {
                Column {
                    Text(
                        text = "选择要使用的下载引擎：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ListItem(
                        headlineContent = { Text("内置多节点多线程下载器") },
                        supportingContent = { Text("并发向每一个 CDN 节点拉取分片，支持断点续传") },
                        leadingContent = { Icon(imageVector = Icons.Default.Speed, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.startInternalDownload()
                            ZakoToast.show("已启动内置多节点下载")
                        }
                    )

                    ListItem(
                        headlineContent = { Text("外部下载器 (1DM / ADM)") },
                        supportingContent = { Text("透传视频直链与 HTTP 请求头唤起外部下载器") },
                        leadingContent = { Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null) },
                        modifier = Modifier.clickable {
                            viewModel.startExternalDownload(context)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDownloadChoice() }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 详情页原位内嵌 MPV 播放器与轻量浮层控制栏
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

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // 播放期间保持屏幕常亮
    DisposableEffect(state.isInlinePlaying) {
        if (state.isInlinePlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
            .background(Color.Black)
            .then(
                if (state.isInlinePlaying) {
                    Modifier.playerGestureDetector(
                        isLocked = playerState.isLocked,
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
                } else {
                    Modifier
                }
            )
    ) {
        // 1. MPV SurfaceView: 保持在稳定的组合树最底层节点中，绝不被条件分支卸载或替换
        key(playerState.engine) {
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
                    view.aspectRatioMode = playerState.aspectRatio
                    view.videoWidth = playerState.videoWidth
                    view.videoHeight = playerState.videoHeight
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. 未播放封面图覆盖层 (Overlay): 覆盖在 AndroidView 上方，点击直接起播，不卸载底层播放器
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
                        .then(
                            if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier
                        ),
                    contentScale = ContentScale.Crop
                )

                // 顶部返回按钮
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 播放图标背景与矢量图标
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // VIP 抢先看徽章
                if (video.isVipEarlyAccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFD54F),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))
                                    )
                                )
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "VIP抢先看",
                                tint = Color(0xFF2E1C00),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {

            // 2. 缓冲指示器
            if (playerState.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            // 3. 手势 HUD 反馈浮层 (播放/暂停、快进退、亮度、音量、倍速加速)
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
                        modifier = Modifier
                            .padding(PlayerControlDefaults.LockContainerPadding)
                            .size(PlayerControlDefaults.LockIconSize)
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
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isFullscreen) {
                                        onToggleFullscreen()
                                    } else {
                                        onNavigateBack()
                                    }
                                },
                                modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isFullscreen) "退出全屏" else "返回",
                                    tint = Color.White,
                                    modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                                )
                            }

                            if (isFullscreen) {
                                Text(
                                    text = video.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = PlayerControlDefaults.ControlSpacing)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            // 画面比例切换 (适应屏幕、填充、16:9、4:3) - 方正圆角画框矢量图标
                            IconButton(
                                onClick = {
                                    val nextMode = viewModel.cycleAspectRatio()
                                    ZakoToast.show("画面比例: ${nextMode.label}")
                                },
                                modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                            ) {
                                Icon(
                                    imageVector = ZakoIcons.AspectRatio,
                                    contentDescription = "画面比例 (${playerState.aspectRatio.label})",
                                    tint = Color.White,
                                    modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                                )
                            }

                            // 更多选项菜单 (⋮)
                            Box {
                                IconButton(
                                    onClick = { viewModel.showMoreMenu(true) },
                                    modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "更多选项",
                                        tint = Color.White,
                                        modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                                    )
                                }

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

                                    // 6. 播放内核切换 (系统 / MPV)
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("播放内核切换")
                                                Text(
                                                    text = "当前: ${playerState.engine.label}",
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
                                                    text = "当前: ${playerState.hwDecMode.label}",
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
                            modifier = Modifier
                                .padding(PlayerControlDefaults.LockContainerPadding)
                                .size(PlayerControlDefaults.LockIconSize)
                        )
                    }

                    // 底部控制条 (波浪进度条、播放/暂停、时间、倍速、全屏按钮)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
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
                                onValueChange = { ratio ->
                                    isScrubbing = true
                                    scrubProgress = ratio
                                },
                                onValueChangeFinished = { ratio ->
                                    isScrubbing = false
                                    val now = System.currentTimeMillis()
                                    if (now - lastSeekTriggerTime >= 150L) {
                                        lastSeekTriggerTime = now
                                        val target = (ratio * playerState.durationMs).toLong()
                                        viewModel.seekTo(target)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
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
                                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "播放/暂停",
                                            tint = Color.White,
                                            modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(PlayerControlDefaults.ControlSpacing))

                                    val displayPosMs = if (isScrubbing) (scrubProgress * playerState.durationMs).toLong() else playerState.currentPositionMs
                                    val displayPosFormatted = moe.zako.viewer.core.player.VideoPlayerState.formatTime(displayPosMs)
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
                                    // 播放倍速切换按钮 - 动态倍速矢量徽标
                                    val currentSpeed = if (playerState.isSpeedBoosted) playerState.boostSpeed else playerState.normalSpeed
                                    IconButton(
                                        onClick = { viewModel.showSpeedDialog(true) },
                                        modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                                    ) {
                                        SpeedBadge(
                                            speed = currentSpeed,
                                            tint = Color.White
                                        )
                                    }

                                    // 全屏 / 退出全屏按钮（唯一全屏入口，位于控制栏右下角）
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
        AlertDialog(
            onDismissRequest = { viewModel.showSpeedDialog(false) },
            title = { Text("选择播放倍速") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setPlaybackSpeed(speed) }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${speed}x", fontWeight = if (playerState.normalSpeed == speed) FontWeight.Bold else FontWeight.Normal)
                            if (playerState.normalSpeed == speed) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showSpeedDialog(false) }) { Text("取消") }
            }
        )
    }

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
                                    Text(
                                        text = "测速指标: ${node.displayMetric}",
                                        color = if (node.testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    if (isCurrent) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable { viewModel.selectCdnNode(node) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCdnDialog() }) { Text("关闭") }
            }
        )
    }

    if (state.showEngineDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showEngineDialog(false) },
            title = { Text("切换播放内核") },
            text = {
                Column {
                    PlayerEngine.values().forEach { eng ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.switchEngine(eng) }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(eng.label, fontWeight = if (playerState.engine == eng) FontWeight.Bold else FontWeight.Normal)
                            if (playerState.engine == eng) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showEngineDialog(false) }) { Text("取消") }
            }
        )
    }

    if (state.showHwDecDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showHwDecDialog(false) },
            title = { Text("硬件解码设置") },
            text = {
                Column {
                    HwDecMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.switchHwDecMode(mode) }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(mode.label, fontWeight = if (playerState.hwDecMode == mode) FontWeight.Bold else FontWeight.Normal)
                            if (playerState.hwDecMode == mode) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.showHwDecDialog(false) }) { Text("取消") }
            }
        )
    }
}

/**
 * 视频标题与基本信息头部 (SelectionContainer 包裹支持复制)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailHeader(
    video: CosVideoItem,
    context: Context
) {
    SelectionContainer {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (video.isVipEarlyAccess) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = VipGold,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "VIP",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "VIP抢先看",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .combinedClickable(
                            onClick = {
                                copyText(context, video.title)
                                ZakoToast.show("已复制")
                            },
                            onLongClick = {
                                copyText(context, video.title)
                                ZakoToast.show("已复制")
                            }
                        )
                )
            }
            if (video.canPlayMsg?.isNotEmpty() == true) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "提示: ${video.canPlayMsg}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放: ${video.displayViews}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val durText = video.formattedDuration
                if (durText.isNotEmpty()) {
                    Text(
                        text = "时长: $durText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 核心操作栏 (收藏、下载、稍后再看、分享，纯矢量等大对齐)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailActionButtons(
    viewModel: VideoDetailViewModel,
    state: VideoDetailUiState,
    video: CosVideoItem,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. 收藏按钮
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (state.isFavorite) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    viewModel.onFavoriteClick()
                    if (state.isFavorite) {
                        ZakoToast.show("已取消收藏")
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. 下载按钮
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when (state.downloadStatus) {
                "COMPLETED", "DOWNLOADING" -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {
                        when (state.downloadStatus) {
                            "COMPLETED" -> ZakoToast.show("该视频已下载完成")
                            "DOWNLOADING" -> ZakoToast.show("视频正在下载中")
                            else -> {
                                if (video.allVideoLines.isEmpty() && video.allMp4Lines.isEmpty()) {
                                    val tip = video.canPlayMsg?.takeIf { it.isNotBlank() } ?: "VIP抢先看视频，暂无有效下载源"
                                    ZakoToast.show(tip)
                                } else {
                                    viewModel.onDownloadClick()
                                    ZakoToast.show("已加入内置多节点多线程下载队列")
                                }
                            }
                        }
                    },
                    onLongClick = { viewModel.onDownloadLongClick() }
                )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (state.downloadStatus) {
                    "DOWNLOADING" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    "COMPLETED" -> {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = "已下载",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "下载",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 3. 稍后再看按钮
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (state.isWatchLater) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    viewModel.toggleWatchLater()
                    val msg = if (!state.isWatchLater) "已加入稍后再看" else "已从稍后再看移除"
                    ZakoToast.show(msg)
                }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (state.isWatchLater) ZakoIcons.WatchLaterFilled else ZakoIcons.WatchLaterOutlined,
                    contentDescription = "稍后再看",
                    tint = if (state.isWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 4. 分享按钮 (短按生成汉字暗号，长按受隐藏开关控制复制完整链接)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {
                        val cipher = ShareCipherUtil.encode(video.id)
                        copyText(context, cipher, "ZakoViewer Share")
                        ZakoToast.show("已复制")
                    },
                    onLongClick = {
                        if (state.isLongPressShareUnlocked && state.isLongPressShareEnabled) {
                            val fullLink = video.allVideoLines.firstOrNull() ?: video.allMp4Lines.firstOrNull() ?: "https://cosav.net/video/${video.id}"
                            copyText(context, fullLink, "ZakoViewer Full Link")
                            ZakoToast.show("已复制完整链接")
                        }
                    }
                )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 元数据展示卡片 (上架日期、番号、登场人物、作品、片商、标签，支持点击跳转检索与文本复制)
 */
@Composable
fun DetailMetadataCard(
    video: CosVideoItem,
    context: Context,
    onNavigateToSearch: (query: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(14.dp)) {
                ClickableMetadataRow(
                    label = "上架日期",
                    value = video.adddate ?: "未知",
                    onCopy = {
                        copyText(context, video.adddate ?: "")
                        ZakoToast.show("已复制")
                    }
                )

                val barcodeVal = video.barcode ?: video.id
                ClickableMetadataRow(
                    label = "番号",
                    value = barcodeVal,
                    onCopy = {
                        copyText(context, barcodeVal)
                        ZakoToast.show("已复制")
                    },
                    onClickSearch = { onNavigateToSearch(barcodeVal) }
                )

                val roleVal = video.cosRole ?: video.author ?: "未知"
                ClickableMetadataRow(
                    label = "登场人物",
                    value = roleVal,
                    onCopy = {
                        copyText(context, roleVal)
                        ZakoToast.show("已复制")
                    },
                    onClickSearch = if (roleVal != "未知") { { onNavigateToSearch(roleVal) } } else null
                )

                val worksVal = video.cosWorks ?: "未知"
                ClickableMetadataRow(
                    label = "作品",
                    value = worksVal,
                    onCopy = {
                        copyText(context, worksVal)
                        ZakoToast.show("已复制")
                    },
                    onClickSearch = if (worksVal != "未知") { { onNavigateToSearch(worksVal) } } else null
                )

                val companyVal = video.company ?: video.series ?: "未知"
                ClickableMetadataRow(
                    label = "片商",
                    value = companyVal,
                    onCopy = {
                        copyText(context, companyVal)
                        ZakoToast.show("已复制")
                    },
                    onClickSearch = if (companyVal != "未知") { { onNavigateToSearch(companyVal) } } else null
                )

                // 标签 (Tags) 支持点击跳转搜索
                if (video.tagList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "标签:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(video.tagList) { tag ->
                            SuggestionChip(
                                onClick = { onNavigateToSearch(tag) },
                                label = { Text(tag, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单行元数据组件 (带复制图标与搜索跳转)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableMetadataRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    onClickSearch: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (onClickSearch != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (onClickSearch != null) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = {
                        if (onClickSearch != null) {
                            onClickSearch()
                        } else {
                            onCopy()
                        }
                    },
                    onLongClick = onCopy
                )
        )
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 详情页留言区卡片 (支持展开/收起)
 */
@Composable
fun DetailCommentsCard(
    video: CosVideoItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val comments = video.comments ?: emptyList()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "留言区 (${comments.size} 条评论)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "展开/折叠"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (comments.isEmpty()) {
                        Text(
                            text = "暂无留言，快来抢沙发吧~",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        comments.forEach { comment ->
                            CommentItem(comment)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(comment: CosComment) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = comment.displayAuthor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            val timeText = comment.addtime ?: comment.createdAt
            timeText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = comment.displayContent,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun copyText(context: Context, text: String, label: String = "ZakoViewer") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
