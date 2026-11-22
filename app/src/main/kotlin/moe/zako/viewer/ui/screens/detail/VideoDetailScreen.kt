package moe.zako.viewer.ui.screens.detail

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.PlayerBrightnessHelper
import moe.zako.viewer.ui.components.ZakoToast

/**
 * 视频详情视窗主体骨架
 * 统筹平板双栏横屏/手机竖屏响应式布局骨架、全屏边到边沉浸、返回拦截与业务弹窗宿主
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // 全屏模式下的屏幕旋转与状态栏沉浸式隐藏调度
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

    // 全屏下优先拦截系统返回键退出全屏
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
            PlayerBrightnessHelper.restoreWindowBrightness(activity)
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
                Text(text = state.errorMessage ?: "视频信息加载失败")
            }
        } else {
            if (state.isFullscreen) {
                BackHandler { viewModel.setFullscreen(false) }
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
                        // 左栏 (58% 宽度): 16:9 内嵌播放器 + 标题、元数据、操作栏与留言区
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

                            DetailHeader(video = video, context = context)

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

                        // 右栏 (42% 宽度): 独立可滚动的相关推荐视频
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

                            DetailRelatedVideosGrid(
                                relatedList = relatedList,
                                onNavigateToDetail = onNavigateToDetail,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // 手机端与竖屏模式：保持播放器在单一稳定的组合节点中（避免滑动复用销毁）
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
                            item {
                                DetailHeader(video = video, context = context)
                            }

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

                            item {
                                DetailMetadataCard(
                                    video = video,
                                    context = context,
                                    onNavigateToSearch = onNavigateToSearch
                                )
                            }

                            item {
                                DetailCommentsCard(
                                    video = video,
                                    isExpanded = state.isCommentsExpanded,
                                    onToggleExpand = { viewModel.toggleCommentsExpanded() }
                                )
                            }

                            detailRelatedVideosSection(
                                relatedList = relatedList,
                                onNavigateToDetail = onNavigateToDetail
                            )
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
        DetailDownloadChoiceDialog(
            onDismissRequest = { viewModel.dismissDownloadChoice() },
            onStartInternalDownload = {
                viewModel.startInternalDownload()
                ZakoToast.show("已启动内置多节点下载")
            },
            onStartExternalDownload = {
                viewModel.startExternalDownload(context)
            }
        )
    }
}
