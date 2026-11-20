package moe.zako.viewer.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import moe.zako.viewer.ui.components.ZakoToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import moe.zako.viewer.core.share.ShareCipherUtil
import moe.zako.viewer.data.source.cosav.CosComment
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.components.VideoCard
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.NeoIcons
import moe.zako.viewer.ui.theme.VipGold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (videoId: String) -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val video = state.video

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(video?.title ?: "视频详情", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (video == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(state.errorMessage ?: "视频信息加载失败")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. Preview / Cover Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                            .clickable {
                                if (video.allVideoLines.isEmpty() && video.allMp4Lines.isEmpty()) {
                                    val tip = video.canPlayMsg?.takeIf { it.isNotBlank() } ?: "VIP抢先看视频，暂无有效播放源"
                                    ZakoToast.show(tip)
                                } else {
                                    onNavigateToPlayer(video.id)
                                }
                            }
                    ) {
                        val isCoverBlurEnabled = LocalCoverBlurEnabled.current
                        AsyncImage(
                            model = video.displayCover,
                            contentDescription = video.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isCoverBlurEnabled) Modifier.blur(18.dp).clipToBounds() else Modifier
                                ),
                            contentScale = ContentScale.Crop
                        )

                        // Play Button Scrim & Icon
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

                        // VIP 抢先看矢量图标 (位于封面左下角，纯矢量图标无文字)
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
                }

                // 2. Title & Metadata Header
                item {
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
                                modifier = Modifier.weight(1f, fill = false)
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

                // 3. 核心操作栏 (纯矢量图标化四按钮：收藏、下载、稍后再看、分享，严格等大对齐)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. 收藏按钮 (未收藏空心，已收藏实心且高亮)
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

                        // 2. 下载按钮 (未下载空心，下载中圆形进度条，下载完成实心完成图标)
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

                        // 3. 稍后再看按钮 (未添加空心，已添加实心且高亮)
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
                                    imageVector = if (state.isWatchLater) NeoIcons.WatchLaterFilled else NeoIcons.WatchLaterOutlined,
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
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("NeoViewer Share", cipher)
                                        clipboard.setPrimaryClip(clip)
                                        ZakoToast.show("已复制")
                                    },
                                    onLongClick = {
                                        if (state.isLongPressShareUnlocked && state.isLongPressShareEnabled) {
                                            val fullLink = video.allVideoLines.firstOrNull() ?: video.allMp4Lines.firstOrNull() ?: "https://cosav.net/video/${video.id}"
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("NeoViewer Full Link", fullLink)
                                            clipboard.setPrimaryClip(clip)
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 4. 元数据展示 (上架日期、登场人物、作品、片商、标签)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            MetadataRow("上架日期", video.adddate ?: "未知")
                            MetadataRow("番号", video.barcode ?: "未知")
                            MetadataRow("登场人物", video.cosRole ?: video.author ?: "未知")
                            MetadataRow("作品", video.cosWorks ?: "未知")
                            MetadataRow("片商", video.company ?: video.series ?: "未知")

                            // 标签 (Tags)
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
                                            onClick = { /* Could filter by tag */ },
                                            label = { Text(tag, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. 留言区 (默认处于折叠收起状态，点击展开)
                item {
                    val comments = video.comments ?: emptyList()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .clickable { viewModel.toggleCommentsExpanded() },
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
                                    imageVector = if (state.isCommentsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "展开/折叠"
                                )
                            }

                            // Collapsible comments body
                            AnimatedVisibility(visible = state.isCommentsExpanded) {
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
                                            Divider(modifier = Modifier.padding(vertical = 6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. 相关视频 (猜你喜欢)
                val related = video.relatedVideos ?: emptyList()
                if (related.isNotEmpty()) {
                    item {
                        Text(
                            text = "相关视频",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }

                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(related) { relItem ->
                                Box(modifier = Modifier.width(180.dp)) {
                                    VideoCard(
                                        video = relItem,
                                        showVipBadge = true,
                                        onClick = { onNavigateToDetail(relItem.id) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Favorite folder selection dialog
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

    // Long press download choice dialog (内置下载器 vs 外部下载器)
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

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
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
            color = MaterialTheme.colorScheme.onSurface
        )
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
