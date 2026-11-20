package moe.zako.viewer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import moe.zako.viewer.NeoApplication
import moe.zako.viewer.core.player.NeoPlayer
import moe.zako.viewer.ui.components.NeoBottomBar
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
fun NeoNavHost(
    navController: NavHostController,
    app: NeoApplication
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Favorites.route,
        Screen.Profile.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NeoBottomBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. 推荐 (首页)
            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = viewModel {
                    HomeViewModel(settingsRepository = app.settingsRepository)
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
                    onNavigateToPlayer = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    },
                    onNavigateToDetail = { videoId ->
                        navController.navigate(Screen.VideoDetail.createRoute(videoId))
                    }
                )
            }

            // 3. 我的 (个人中心与系统功能)
            composable(Screen.Profile.route) {
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
                    onNavigateToAbout = { navController.navigate(Screen.About.route) }
                )
            }

            // 4. 视频详情页
            composable(
                route = Screen.VideoDetail.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                val viewModel: VideoDetailViewModel = viewModel {
                    VideoDetailViewModel(
                        videoId = videoId,
                        favoriteRepository = app.favoriteRepository,
                        historyRepository = app.historyRepository,
                        settingsRepository = app.settingsRepository,
                        downloadManager = app.downloadManager
                    )
                }
                VideoDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPlayer = { id ->
                        navController.navigate(Screen.Player.createRoute(id))
                    },
                    onNavigateToDetail = { id ->
                        navController.navigate(Screen.VideoDetail.createRoute(id))
                    }
                )
            }

            // 5. 视频播放页 (MPV 内核)
            composable(
                route = Screen.Player.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { backStackEntry ->
                val context = LocalContext.current
                val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                val viewModel: PlayerViewModel = viewModel {
                    PlayerViewModel(
                        videoId = videoId,
                        player = NeoPlayer(context),
                        favoriteRepository = app.favoriteRepository,
                        historyRepository = app.historyRepository,
                        settingsRepository = app.settingsRepository
                    )
                }
                PlayerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 6. 离线下载
            composable(Screen.Downloads.route) {
                val viewModel: DownloadsViewModel = viewModel {
                    DownloadsViewModel(
                        downloadManager = app.downloadManager,
                        settingsRepository = app.settingsRepository
                    )
                }
                DownloadsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPlayDownloadedVideo = { videoId ->
                        navController.navigate(Screen.Player.createRoute(videoId))
                    }
                )
            }

            // 7. 观看记录
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
                    onNavigateToDetail = { id ->
                        navController.navigate(Screen.VideoDetail.createRoute(id))
                    }
                )
            }

            // 8. 稍后再看
            composable(Screen.WatchLater.route) {
                val viewModel: WatchLaterViewModel = viewModel {
                    WatchLaterViewModel(historyRepository = app.historyRepository)
                }
                WatchLaterScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id ->
                        navController.navigate(Screen.VideoDetail.createRoute(id))
                    }
                )
            }

            // 9. 设置主菜单
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAppearance = { navController.navigate(Screen.SettingsAppearance.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.SettingsPrivacy.route) },
                    onNavigateToShare = { navController.navigate(Screen.SettingsShare.route) },
                    onNavigateToPlayer = { navController.navigate(Screen.SettingsPlayer.route) },
                    onNavigateToDownload = { navController.navigate(Screen.SettingsDownload.route) },
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
                    SettingsViewModel(settingsRepository = app.settingsRepository)
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
