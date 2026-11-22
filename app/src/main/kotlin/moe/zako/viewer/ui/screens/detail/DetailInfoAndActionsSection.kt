package moe.zako.viewer.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.zako.viewer.core.share.ShareCipherUtil
import moe.zako.viewer.data.source.cosav.CosVideoItem
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.VipGold

/**
 * 详情页视频标题与基本信息头部 (SelectionContainer 包裹支持复制)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailHeader(
    video: CosVideoItem,
    context: Context,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SelectionContainer {
            Column {
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
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            copyText(context, video.title)
                            ZakoToast.show("已复制视频标题")
                        }
                    )
                )
            }
        }
    }
}

/**
 * 详情页核心操作按钮栏 (收藏五角星、下载、稍后再看、分享)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailActionButtons(
    viewModel: VideoDetailViewModel,
    state: VideoDetailUiState,
    video: CosVideoItem,
    context: Context,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. 收藏按钮 (全局对齐五角星 Design Token)
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
                    imageVector = if (state.isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. 下载按钮 (短按加入多节点队列，长按呼出外部下载方式)
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
                            imageVector = AppIcons.Download,
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
                    imageVector = if (state.isWatchLater) AppIcons.WatchLaterFilled else AppIcons.WatchLaterOutlined,
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
                    imageVector = AppIcons.Share,
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
    onNavigateToSearch: (query: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
                imageVector = AppIcons.Copy,
                contentDescription = "复制",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

fun copyText(context: Context, text: String, label: String = "ZakoViewer") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
