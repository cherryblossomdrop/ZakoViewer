package moe.zako.viewer.ui.screens.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import moe.zako.viewer.core.cover.CoverCacheManager
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.ui.components.FolderTreeDialog
import moe.zako.viewer.ui.theme.LocalCoverBlurEnabled
import moe.zako.viewer.ui.theme.OverlayBlack
import moe.zako.viewer.ui.theme.VipGold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToPlayer: (videoId: String) -> Unit,
    onNavigateToDetail: (videoId: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val coverBlurEnabled = LocalCoverBlurEnabled.current

    // Dialog & Menu States
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FavoriteFolderEntity?>(null) }
    var renameFolderNameInput by remember { mutableStateOf("") }

    var itemToRename by remember { mutableStateOf<FavoriteItemEntity?>(null) }
    var renameItemTitleInput by remember { mutableStateOf("") }

    var folderMenuTarget by remember { mutableStateOf<FavoriteFolderEntity?>(null) }
    var itemMenuTarget by remember { mutableStateOf<FavoriteItemEntity?>(null) }

    var localVideoInfoToShow by remember { mutableStateOf<LocalVideoInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isSelectionMode) {
                            "已选择 ${state.totalSelectedCount} 项"
                        } else {
                            state.currentFolder?.name ?: "收藏夹"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (state.isSelectionMode) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("全选")
                        }
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "退出多选")
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(imageVector = Icons.Default.CheckCircleOutline, contentDescription = "多选管理")
                        }
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = "新建子收藏夹")
                        }
                        // 重命名当前收藏夹（包含根收藏夹与子收藏夹）
                        state.currentFolder?.let { cur ->
                            IconButton(onClick = {
                                renameFolderNameInput = cur.name
                                folderToRename = cur
                            }) {
                                Icon(imageVector = Icons.Default.DriveFileRenameOutline, contentDescription = "重命名当前收藏夹")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Batch Action Bottom Bar
            if (state.isSelectionMode && state.totalSelectedCount > 0) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.showMoveDialogForSelection() }) {
                            Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("移动 (${state.totalSelectedCount})")
                        }

                        TextButton(
                            onClick = { viewModel.showCopyDialogForSelection() },
                            enabled = state.selectedItemIds.isNotEmpty()
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制 (${state.selectedItemIds.size})")
                        }

                        TextButton(onClick = { viewModel.batchDeleteSelected() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除 (${state.totalSelectedCount})", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Breadcrumbs Bar
            if (state.breadcrumbs.size > 1) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.breadcrumbs) { folder ->
                            val isLast = folder.id == state.breadcrumbs.lastOrNull()?.id
                            AssistChip(
                                onClick = { viewModel.navigateToBreadcrumb(folder) },
                                label = { Text(folder.name, fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (folder.parentId == null) {
                                    { Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = if (isLast) {
                                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else AssistChipDefaults.assistChipColors()
                            )
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. In-folder Search Bar (占位文本已按需求彻底移除)
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "清空")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    singleLine = true
                )
            }

            // 3. Subfolders Section (列表形式显示，递归搜索时隐藏以展示搜索结果)
            if (state.searchQuery.isEmpty() && state.subFolders.isNotEmpty()) {
                item {
                    Text(
                        text = "子收藏夹 (${state.subFolders.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp)
                    )
                }

                items(state.subFolders, key = { "folder_${it.id}" }) { folder ->
                    val isFolderSelected = state.selectedFolderIds.contains(folder.id)
                    val isRootFolder = folder.parentId == null

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFolderSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .combinedClickable(
                                onClick = {
                                    if (state.isSelectionMode) {
                                        if (!isRootFolder) viewModel.toggleFolderSelection(folder.id)
                                    } else {
                                        viewModel.openFolder(folder)
                                    }
                                },
                                onLongClick = {
                                    if (!isRootFolder) {
                                        folderMenuTarget = folder
                                    }
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isSelectionMode && !isRootFolder) {
                                Checkbox(
                                    checked = isFolderSelected,
                                    onCheckedChange = { viewModel.toggleFolderSelection(folder.id) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isFolderSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!state.isSelectionMode) {
                                IconButton(onClick = { folderMenuTarget = folder }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "菜单",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Divider between folders and items
            if (state.subFolders.isNotEmpty() && state.items.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            // 4. Video Items Section (列表形式显示，点击直接播放视频)
            if (state.items.isEmpty() && state.subFolders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.searchQuery.isNotEmpty()) "未匹配到相关收藏视频" else "当前收藏夹为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (state.items.isNotEmpty()) {
                item {
                    Text(
                        text = if (state.searchQuery.isNotEmpty()) "递归检索结果 (${state.items.size})" else "视频列表 (${state.items.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp)
                    )
                }

                items(state.items, key = { "item_${it.id}" }) { item ->
                    val isSelected = state.selectedItemIds.contains(item.id)

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp)
                            .combinedClickable(
                                onClick = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        // 从收藏夹点击视频应该直接开始播放视频，而非进入视频详情页
                                        onNavigateToPlayer(item.videoId)
                                    }
                                },
                                onLongClick = {
                                    itemMenuTarget = item
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleItemSelection(item.id) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }

                            // 视频封面卡片
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clipToBounds()
                            ) {
                                AsyncImage(
                                    model = CoverCacheManager.getCoverModel(LocalContext.current, item.videoId, item.coverUrl),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(if (coverBlurEnabled) Modifier.blur(18.dp) else Modifier)
                                )

                                if (item.isVip) {
                                    Surface(
                                        color = VipGold,
                                        shape = RoundedCornerShape(bottomEnd = 4.dp),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WorkspacePremium,
                                            contentDescription = "VIP",
                                            tint = Color(0xFF2E1C00),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(2.dp)
                                        )
                                    }
                                }

                                if (item.duration.isNotBlank()) {
                                    Surface(
                                        color = OverlayBlack,
                                        shape = RoundedCornerShape(3.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                     ) {
                                        Text(
                                            text = item.duration,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 标题与信息
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "视频编号: ${item.videoId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    if (state.searchQuery.isNotEmpty() && item.folderId != state.currentFolder?.id) {
                                        val parentFolder = state.allFolders.firstOrNull { it.id == item.folderId }
                                        if (parentFolder != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "来自: ${parentFolder.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // 菜单触发器
                            if (!state.isSelectionMode) {
                                IconButton(onClick = { itemMenuTarget = item }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "选项",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 1. 长按子收藏夹弹出菜单 (多选、重命名、删除、移动)
    // ==========================================
    folderMenuTarget?.let { folder ->
        ModalBottomSheet(onDismissRequest = { folderMenuTarget = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("多选") },
                    leadingContent = { Icon(Icons.Default.Checklist, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val fid = folder.id
                        folderMenuTarget = null
                        viewModel.startSelectionWithFolder(fid)
                    })
                )

                ListItem(
                    headlineContent = { Text("重命名") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val f = folder
                        folderMenuTarget = null
                        renameFolderNameInput = f.name
                        folderToRename = f
                    })
                )

                ListItem(
                    headlineContent = { Text("移动到") },
                    leadingContent = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val fid = folder.id
                        folderMenuTarget = null
                        viewModel.showMoveDialogForSingleFolder(fid)
                    })
                )

                ListItem(
                    headlineContent = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val fid = folder.id
                        folderMenuTarget = null
                        viewModel.deleteFolder(fid)
                    })
                )
            }
        }
    }

    // ==========================================
    // 2. 长按视频项目弹出菜单 (多选、重命名、删除、移动、复制、查看详情)
    // ==========================================
    itemMenuTarget?.let { item ->
        ModalBottomSheet(onDismissRequest = { itemMenuTarget = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 1. 多选
                ListItem(
                    headlineContent = { Text("多选") },
                    leadingContent = { Icon(Icons.Default.Checklist, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val iid = item.id
                        itemMenuTarget = null
                        viewModel.startSelectionWithItem(iid)
                    })
                )

                // 2. 重命名
                ListItem(
                    headlineContent = { Text("重命名") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val itm = item
                        itemMenuTarget = null
                        renameItemTitleInput = itm.title
                        itemToRename = itm
                    })
                )

                // 3. 移动
                ListItem(
                    headlineContent = { Text("移动到") },
                    leadingContent = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val iid = item.id
                        itemMenuTarget = null
                        viewModel.showMoveDialogForSingleItem(iid)
                    })
                )

                // 4. 复制
                ListItem(
                    headlineContent = { Text("复制到") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val itm = item
                        itemMenuTarget = null
                        viewModel.showCopyDialogForSingleItem(itm)
                    })
                )

                // 5. 查看详情 (本地视频弹窗显示文件信息；在线视频跳转视频详情页)
                ListItem(
                    headlineContent = { Text("查看详情") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val itm = item
                        itemMenuTarget = null
                        scope.launch {
                            val localInfo = viewModel.checkLocalVideoInfo(itm)
                            if (localInfo != null) {
                                localVideoInfoToShow = localInfo
                            } else {
                                onNavigateToDetail(itm.videoId)
                            }
                        }
                    })
                )

                // 6. 删除
                ListItem(
                    headlineContent = { Text("从收藏夹删除", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.combinedClickable(onClick = {
                        val iid = item.id
                        itemMenuTarget = null
                        viewModel.deleteItem(iid)
                    })
                )
            }
        }
    }

    // ==========================================
    // 3. 本地视频文件信息弹窗
    // ==========================================
    localVideoInfoToShow?.let { info ->
        AlertDialog(
            onDismissRequest = { localVideoInfoToShow = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.VideoFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("本地视频文件信息")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "视频名称: ${info.title}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(text = "文件大小: ${info.fileSizeFormatted}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "下载/创建时间: ${info.createdAtFormatted}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "时长: ${info.duration}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "存储路径:\n${info.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { localVideoInfoToShow = null }) {
                    Text("确定")
                }
            }
        )
    }

    // ==========================================
    // 4. 新建子收藏夹弹窗 (用户不能创建根收藏夹)
    // ==========================================
    if (showCreateFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新建子收藏夹") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("收藏夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            viewModel.createFolder(folderNameInput.trim(), state.currentFolder?.id)
                            showCreateFolderDialog = false
                        }
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("取消") }
            }
        )
    }

    // ==========================================
    // 5. 重命名收藏夹弹窗
    // ==========================================
    folderToRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("重命名收藏夹") },
            text = {
                OutlinedTextField(
                    value = renameFolderNameInput,
                    onValueChange = { renameFolderNameInput = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameFolderNameInput.isNotBlank()) {
                            viewModel.renameFolder(folder.id, renameFolderNameInput.trim())
                            folderToRename = null
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) { Text("取消") }
            }
        )
    }

    // ==========================================
    // 6. 重命名视频标题弹窗
    // ==========================================
    itemToRename?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("修改收藏视频名称") },
            text = {
                OutlinedTextField(
                    value = renameItemTitleInput,
                    onValueChange = { renameItemTitleInput = it },
                    label = { Text("视频标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameItemTitleInput.isNotBlank()) {
                            viewModel.renameItem(item.id, renameItemTitleInput.trim())
                            itemToRename = null
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("取消") }
            }
        )
    }

    // ==========================================
    // 7. 移动目标选择弹窗 (支持批量移动 / 单项移动)
    // ==========================================
    if (state.showFolderSelectorForMove) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissMoveDialog() },
            onFolderSelected = { targetId -> viewModel.executeMove(targetId) },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) }
        )
    }

    // ==========================================
    // 8. 复制目标选择弹窗 (支持批量复制 / 单项复制)
    // ==========================================
    if (state.showFolderSelectorForCopy) {
        FolderTreeDialog(
            folders = state.allFolders,
            onDismiss = { viewModel.dismissCopyDialog() },
            onFolderSelected = { targetId -> viewModel.executeCopy(targetId) },
            onCreateFolder = { name, parentId -> viewModel.createFolder(name, parentId) }
        )
    }
}
