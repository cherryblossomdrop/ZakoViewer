package moe.zako.viewer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 全局图标 Design Token
 * 集中统一管理应用内的矢量图标引用，禁止业务 UI 硬编码 Material Icons 字面量
 */
object AppIcons {
    // 多选：以「离线下载」当前使用的 Checklist 图标为全应用唯一权威值
    val MultiSelect: ImageVector = Icons.Default.Checklist

    // 收藏相关
    val AddToFavorites: ImageVector = Icons.Default.Favorite
    val Favorite: ImageVector = Icons.Default.Favorite
    val FavoriteBorder: ImageVector = Icons.Default.FavoriteBorder

    // 稍后再看
    val WatchLater: ImageVector = ZakoIcons.WatchLater
    val WatchLaterFilled: ImageVector = ZakoIcons.WatchLaterFilled
    val WatchLaterOutlined: ImageVector = ZakoIcons.WatchLaterOutlined

    // 视频详情与播放
    val VideoDetail: ImageVector = Icons.AutoMirrored.Filled.Article
    val Play: ImageVector = Icons.Default.PlayArrow
    val PlayCircle: ImageVector = Icons.Default.PlayCircle
    val Pause: ImageVector = Icons.Default.Pause
    val VideoFile: ImageVector = Icons.Default.VideoFile

    // 操作管理
    val Delete: ImageVector = Icons.Default.Delete
    val DeleteSweep: ImageVector = Icons.Default.DeleteSweep
    val DeleteOutline: ImageVector = Icons.Default.DeleteOutline
    val DeleteForever: ImageVector = Icons.Default.DeleteForever
    val Close: ImageVector = Icons.Default.Close
    val Clear: ImageVector = Icons.Default.Clear
    val ArrowBack: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val ArrowForward: ImageVector = Icons.AutoMirrored.Filled.ArrowForwardIos
    val ChevronRight: ImageVector = Icons.Default.ChevronRight
    val Launch: ImageVector = Icons.AutoMirrored.Filled.Launch
    val Add: ImageVector = Icons.Rounded.Add
    val Edit: ImageVector = Icons.Default.Edit
    val Rename: ImageVector = Icons.Default.DriveFileRenameOutline
    val Copy: ImageVector = Icons.Default.ContentCopy
    val Move: ImageVector = Icons.AutoMirrored.Filled.DriveFileMove
    val Search: ImageVector = Icons.Default.Search
    val MoreVert: ImageVector = Icons.Default.MoreVert
    val WorkspacePremium: ImageVector = Icons.Default.WorkspacePremium
    val CheckCircle: ImageVector = Icons.Default.CheckCircle

    // 设置与系统
    val Palette: ImageVector = Icons.Default.Palette
    val Share: ImageVector = Icons.Default.Share
    val Download: ImageVector = Icons.Default.Download
    val DownloadForOffline: ImageVector = Icons.Default.DownloadForOffline
    val CleaningServices: ImageVector = Icons.Default.CleaningServices
    val Terminal: ImageVector = Icons.Default.Terminal

    // 搜索、浏览与视图模式
    val History: ImageVector = Icons.Default.History
    val Check: ImageVector = Icons.Default.Check
    val ExpandLess: ImageVector = Icons.Default.ExpandLess
    val Tune: ImageVector = Icons.Default.Tune
    val Sort: ImageVector = Icons.AutoMirrored.Filled.Sort
    val GridView: ImageVector = Icons.Default.GridView
    val ViewAgenda: ImageVector = Icons.Default.ViewAgenda

    // 文件与目录
    val FileInfo: ImageVector = Icons.Default.Info
    val Folder: ImageVector = Icons.Default.Folder
    val FolderOpen: ImageVector = Icons.Default.FolderOpen
    val FolderSpecial: ImageVector = Icons.Default.FolderSpecial
    val CreateFolder: ImageVector = Icons.Default.CreateNewFolder
    val SdCard: ImageVector = Icons.Default.SdCard
    val Description: ImageVector = Icons.Default.Description
    val Warning: ImageVector = Icons.Default.Warning
    val WarningAmber: ImageVector = Icons.Default.WarningAmber
    val Security: ImageVector = Icons.Default.Security
}
