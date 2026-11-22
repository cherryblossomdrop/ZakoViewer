package moe.zako.viewer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.zako.viewer.ui.theme.Spacing
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import moe.zako.viewer.ZakoApplication
import moe.zako.viewer.core.player.ZakoPlayer
import moe.zako.viewer.ui.components.ZakoBottomBar
import moe.zako.viewer.ui.screens.detail.VideoDetailScreen
import moe.zako.viewer.ui.screens.detail.VideoDetailViewModel
import moe.zako.viewer.ui.screens.favorites.FavoritesScreen
import moe.zako.viewer.ui.screens.favorites.FavoritesViewModel
import moe.zako.viewer.ui.screens.home.HomeScreen
import moe.zako.viewer.ui.screens.home.HomeViewModel
import moe.zako.viewer.ui.screens.player.PlayerScreen
import moe.zako.viewer.ui.screens.player.PlayerViewModel
import moe.zako.viewer.ui.screens.profile.ProfileScreen
import moe.zako.viewer.ui.screens.profile.about.AboutScreen
import moe.zako.viewer.ui.screens.profile.about.LogViewerScreen
import moe.zako.viewer.ui.screens.profile.backup.BackupRestoreScreen
import moe.zako.viewer.ui.screens.profile.backup.BackupRestoreViewModel
import moe.zako.viewer.ui.screens.profile.downloads.DownloadsScreen
import moe.zako.viewer.ui.screens.profile.downloads.DownloadsViewModel
import moe.zako.viewer.ui.screens.profile.history.HistoryScreen
import moe.zako.viewer.ui.screens.profile.history.HistoryViewModel
import moe.zako.viewer.ui.screens.profile.settings.*
import moe.zako.viewer.ui.screens.profile.watchlater.WatchLaterScreen
import moe.zako.viewer.ui.screens.profile.watchlater.WatchLaterViewModel

@Composable
fun ZakoNavHost(
    navController: NavHostController,
    app: ZakoApplication
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.smallestScreenWidthDp >= 600 &&
            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val showBottomBar = currentRoute?.startsWith("home") == true || currentRoute in listOf(
        Screen.Favorites.route,
        Screen.Profile.route
    )

    val coroutineScope = rememberCoroutineScope()
    // 统一列表点击路由（历史记录、收藏夹、稍后再看）：本地优先播放，未命中则进详情页
    val handleListVideoClick: (String) -> Unit = { videoId ->
        coroutineScope.launch {
            val local = app.downloadManager.findLocalCompletedVideo(videoId)
            if (local != null) {
                navController.navigate(
                    Screen.Player.createRoute(
                        videoId = videoId,
                        localPath = local.file.absolutePath,
                        forceOnline = false
                    )
                )
            } else {
                navController.navigate(Screen.VideoDetail.createRoute(videoId))
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar && !isTabletLandscape) {
                ZakoBottomBar(navController = navController)
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 平板横屏侧边栏 NavigationRail
            if (showBottomBar && isTabletLandscape) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    val items = listOf(
                        Screen.Home,
                        Screen.Favorites,
                        Screen.Profile
                    )
                    items.forEachIndexed { index, screen ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(Spacing.Xl))
                        }
                        val isSelected = currentRoute == screen.route ||
                                (screen == Screen.Home && currentRoute?.startsWith("home") == true)
                        NavigationRailItem(
                            icon = {
                                screen.icon?.let {
                                    Icon(imageVector = it, contentDescription = screen.title)
                                }
                            },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    val dest = if (screen == Screen.Home) Screen.Home.createRoute() else screen.route
                                    navController.navigate(dest) {
                                        popUpTo(Screen.Home.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1.2f))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. 推荐 / 搜索 (首页)
                    composable(
                        route = "home?q={q}",
                        arguments = listOf(navArgument("q") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = true
                        })
                    ) { backStackEntry ->
                        val query = backStackEntry.arguments?.getString("q") ?: ""
                        val viewModel: HomeViewModel = viewModel {
                            HomeViewModel(
                                settingsRepository = app.settingsRepository,
                                searchHistoryRepository = app.searchHistoryRepository
                            )
                        }
                        LaunchedEffect(query) {
                            if (query.isNotEmpty()) {
                                viewModel.onSearchQueryChange(query)
                                viewModel.submitSearch()
                            }
                        }
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToDetail = { videoId ->
                                navController.navigate(Screen.VideoDetail.createRoute(videoId))
                            }
                        )
                    }

                    // 2. 收藏夹 (无限层级纯索引系统)
                    composable(Screen.Favorites.route) {
                        val viewModel: FavoritesViewModel = viewModel {
                            FavoritesViewModel(
                                favoriteRepository = app.favoriteRepository,
                                downloadDao = app.database.downloadDao()
                            )
                        }
                        FavoritesScreen(
                            viewModel = viewModel,
                            onNavigateToPlayer = handleListVideoClick,
                            onNavigateToDetail = { videoId ->
                                navController.navigate(Screen.VideoDetail.createRoute(videoId))
                            }
                        )
                    }

                    // 3. 我的 (个人中心与系统功能)
                    composable(Screen.Profile.route) {
                        val settingsViewModel: SettingsViewModel = viewModel {
                            SettingsViewModel(settingsRepository = app.settingsRepository)
                        }
                        ProfileScreen(
                            onNavigateToDownloads = { navController.navigate(Screen.Downloads.route) },
                            onNavigateToHistory = { navController.navigate(Screen.WatchHistory.route) },
                            onNavigateToWatchLater = { navController.navigate(Screen.WatchLater.route) },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                            onNavigateToAppearance = { navController.navigate(Screen.SettingsAppearance.route) },
                            onNavigateToPlayback = { navController.navigate(Screen.SettingsPlayer.route) },
                            onNavigateToPrivacy = { navController.navigate(Screen.SettingsPrivacy.route) },
                            onNavigateToShare = { navController.navigate(Screen.SettingsShare.route) },
                            onNavigateToSettingsDownload = { navController.navigate(Screen.SettingsDownload.route) },
                            onNavigateToBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                            onNavigateToAbout = { navController.navigate(Screen.About.route) },
                            settingsViewModel = settingsViewModel
                        )
                    }

                    // 4. 视频详情页
                    composable(
                        route = Screen.VideoDetail.route,
                        arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                        popExitTransition = { ExitTransition.None }
                    ) { backStackEntry ->
                        val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                        val viewModel: VideoDetailViewModel = viewModel {
                            VideoDetailViewModel(
                                videoId = videoId,
                                favoriteRepository = app.favoriteRepository,
                                historyRepository = app.historyRepository,
                                settingsRepository = app.settingsRepository,
                                downloadManager = app.downloadManager,
                                player = ZakoPlayer(app.applicationContext)
                            )
                        }
                        VideoDetailScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            // 详情页跳转播放器契约：显式透传 forceOnline = true，严格跳过本地优先拦截并强制走在线 CDN 路由
                            onNavigateToPlayer = { id ->
                                navController.navigate(Screen.Player.createRoute(id, forceOnline = true))
                            },
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.VideoDetail.createRoute(id))
                            },
                            onNavigateToSearch = { query ->
                                navController.navigate(Screen.Home.createRoute(query))
                            }
                        )
                    }

                    // 5. 视频播放页 (MPV 内核)
                    composable(
                        route = Screen.Player.route,
                        arguments = listOf(
                            navArgument("videoId") { type = NavType.StringType },
                            navArgument("localPath") {
                                type = NavType.StringType
                                defaultValue = ""
                                nullable = true
                            },
                            navArgument("forceOnline") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        ),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) { backStackEntry ->
                        val context = LocalContext.current
                        val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                        val rawLocalPath = backStackEntry.arguments?.getString("localPath") ?: ""
                        val forceOnline = backStackEntry.arguments?.getBoolean("forceOnline") ?: false
                        val localPath = if (rawLocalPath.isNotEmpty()) {
                            try { java.net.URLDecoder.decode(rawLocalPath, "UTF-8") } catch (_: Exception) { rawLocalPath }
                        } else ""

                        val viewModel: PlayerViewModel = viewModel {
                            PlayerViewModel(
                                videoId = videoId,
                                initialLocalPath = localPath,
                                forceOnline = forceOnline,
                                player = ZakoPlayer(context),
                                favoriteRepository = app.favoriteRepository,
                                historyRepository = app.historyRepository,
                                settingsRepository = app.settingsRepository,
                                downloadDao = app.database.downloadDao(),
                                downloadManager = app.downloadManager
                            )
                        }
                        PlayerScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { id ->
                                navController.navigate(Screen.VideoDetail.createRoute(id))
                            }
                        )
                    }

                    // 6. 离线下载
                    composable(Screen.Downloads.route) {
                        val viewModel: DownloadsViewModel = viewModel {
                            DownloadsViewModel(
                                downloadManager = app.downloadManager,
                                settingsRepository = app.settingsRepository,
                                favoriteRepository = app.favoriteRepository
                            )
                        }
                        DownloadsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onPlayDownloadedVideo = { videoId, localPath ->
                                navController.navigate(Screen.Player.createRoute(videoId, localPath))
                            },
                            onPlayOnline = { videoId ->
                                navController.navigate(Screen.Player.createRoute(videoId, ""))
                            },
                            onNavigateToDetail = { videoId ->
                                navController.navigate(Screen.VideoDetail.createRoute(videoId))
                            }
                        )
                    }

            // 7. 历史记录
            composable(Screen.WatchHistory.route) {
                val viewModel: HistoryViewModel = viewModel {
                    HistoryViewModel(
                        historyRepository = app.historyRepository,
                        favoriteRepository = app.favoriteRepository
                    )
                }
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = handleListVideoClick
                )
            }

            // 8. 稍后再看
            composable(Screen.WatchLater.route) {
                val viewModel: WatchLaterViewModel = viewModel {
                    WatchLaterViewModel(
                        historyRepository = app.historyRepository,
                        favoriteRepository = app.favoriteRepository
                    )
                }
                WatchLaterScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = handleListVideoClick
                )
            }

            // 9. 设置主菜单
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(settingsRepository = app.settingsRepository)
                }
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAppearance = { navController.navigate(Screen.SettingsAppearance.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.SettingsPrivacy.route) },
                    onNavigateToShare = { navController.navigate(Screen.SettingsShare.route) },
                    onNavigateToPlayer = { navController.navigate(Screen.SettingsPlayer.route) },
                    onNavigateToDownload = { navController.navigate(Screen.SettingsDownload.route) },
                    onNavigateToLogs = { navController.navigate(Screen.LogViewer.route) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9.1 外观设置
            composable(Screen.SettingsAppearance.route) {
                val viewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(settingsRepository = app.settingsRepository)
                }
                SettingsAppearanceScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9.2 隐私设置
            composable(Screen.SettingsPrivacy.route) {
                val viewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(settingsRepository = app.settingsRepository)
                }
                SettingsPrivacyScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9.3 分享设置
            composable(Screen.SettingsShare.route) {
                val viewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(settingsRepository = app.settingsRepository)
                }
                SettingsShareScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9.4 播放设置
            composable(Screen.SettingsPlayer.route) {
                val viewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(settingsRepository = app.settingsRepository)
                }
                SettingsPlayerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9.5 下载设置
            composable(Screen.SettingsDownload.route) {
                val viewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(
                        settingsRepository = app.settingsRepository,
                        downloadDao = app.database.downloadDao(),
                        downloadManager = app.downloadManager
                    )
                }
                SettingsDownloadScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 10. 关于 APP
            composable(Screen.About.route) {
                AboutScreen(
                    settingsRepository = app.settingsRepository,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogs = { navController.navigate(Screen.LogViewer.route) }
                )
            }

            // 11. Log 记录与导出
            composable(Screen.LogViewer.route) {
                LogViewerScreen(onNavigateBack = { navController.popBackStack() })
            }

            // 12. 数据备份与恢复
            composable(Screen.BackupRestore.route) {
                val context = LocalContext.current
                val viewModel: BackupRestoreViewModel = viewModel {
                    BackupRestoreViewModel(context = context)
                }
                BackupRestoreScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
    }
}

@Composable
fun NeoNavHost(
    navController: NavHostController,
    app: ZakoApplication
) = ZakoNavHost(navController, app)
