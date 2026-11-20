package moe.zako.viewer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Home : Screen("home?q={q}", "推荐", Icons.Default.Home) {
        fun createRoute(q: String = ""): String =
            if (q.isNotEmpty()) "home?q=${java.net.URLEncoder.encode(q, "UTF-8")}" else "home"
    }
    object Favorites : Screen("favorites", "收藏夹", Icons.Default.Favorite)
    object Profile : Screen("profile", "我的", Icons.Default.Person)

    object VideoDetail : Screen("video_detail/{videoId}", "视频详情") {
        fun createRoute(videoId: String) = "video_detail/$videoId"
    }

    object Player : Screen("player/{videoId}?localPath={localPath}", "播放器") {
        fun createRoute(videoId: String, localPath: String = ""): String =
            if (localPath.isNotEmpty()) {
                "player/$videoId?localPath=${java.net.URLEncoder.encode(localPath, "UTF-8")}"
            } else {
                "player/$videoId"
            }
    }

    object Downloads : Screen("downloads", "离线下载")
    object WatchHistory : Screen("watch_history", "观看记录")
    object WatchLater : Screen("watch_later", "稍后再看")
    object Settings : Screen("settings", "设置")
    object SettingsAppearance : Screen("settings/appearance", "外观设置")
    object SettingsPrivacy : Screen("settings/privacy", "隐私设置")
    object SettingsShare : Screen("settings/share", "分享设置")
    object SettingsPlayer : Screen("settings/player", "播放设置")
    object SettingsDownload : Screen("settings/download", "下载设置")

    object About : Screen("about", "关于 APP")
    object LogViewer : Screen("log_viewer", "运行日志")
    object BackupRestore : Screen("backup_restore", "备份与恢复")
}
