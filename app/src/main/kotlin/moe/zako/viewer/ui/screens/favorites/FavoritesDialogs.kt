package moe.zako.viewer.ui.screens.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.ui.components.VideoActionBottomSheet
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.theme.AppIcons

/**
 * 子收藏夹长按操作抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionBottomSheet(
    folder: FavoriteFolderEntity,
    onStartSelection: (Long) -> Unit,
    onRename: (FavoriteFolderEntity) -> Unit,
    onMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
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
                leadingContent = { Icon(AppIcons.MultiSelect, contentDescription = null) },
                modifier = Modifier.clickable { onStartSelection(folder.id) }
            )

            ListItem(
                headlineContent = { Text("重命名") },
                leadingContent = { Icon(AppIcons.Edit, contentDescription = null) },
                modifier = Modifier.clickable { onRename(folder) }
            )

            ListItem(
                headlineContent = { Text("移动到") },
                leadingContent = { Icon(AppIcons.Move, contentDescription = null) },
                modifier = Modifier.clickable { onMove(folder.id) }
            )

            ListItem(
                headlineContent = { Text("删除", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onDelete(folder.id) }
            )
        }
    }
}

/**
 * 收藏视频条目长按操作抽屉 (基于 VideoActionBottomSheet)
 */
@Composable
fun FavoriteItemActionBottomSheet(
    item: FavoriteItemEntity,
    onStartSelection: (Long) -> Unit,
    onRename: (FavoriteItemEntity) -> Unit,
    onMove: (Long) -> Unit,
    onCopy: (FavoriteItemEntity) -> Unit,
    onDetail: (FavoriteItemEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissRequest: () -> Unit,
    hasLocalFile: Boolean = false,
    onShowFileInfo: (() -> Unit)? = null
) {
    val actions = buildList {
        add(
            VideoMenuAction(
                id = "multi_select",
                title = "多选",
                icon = AppIcons.MultiSelect,
                onClick = { onStartSelection(item.id) }
            )
        )
        add(
            VideoMenuAction(
                id = "rename",
                title = "重命名",
                icon = AppIcons.Edit,
                onClick = { onRename(item) }
            )
        )
        add(
            VideoMenuAction(
                id = "move",
                title = "移动到",
                icon = AppIcons.Move,
                onClick = { onMove(item.id) }
            )
        )
        add(
            VideoMenuAction(
                id = "copy",
                title = "复制到",
                icon = AppIcons.Copy,
                onClick = { onCopy(item) }
            )
        )
        add(
            VideoMenuAction(
                id = "detail",
                title = "查看详情",
                icon = AppIcons.VideoDetail,
                supportingText = "查看本视频应用内详情页",
                onClick = { onDetail(item) }
            )
        )
        if (hasLocalFile && onShowFileInfo != null) {
            add(
                VideoMenuAction(
                    id = "file_info",
                    title = "文件信息",
                    icon = AppIcons.FileInfo,
                    supportingText = "查看文件名、本地存储路径与大小",
                    onClick = onShowFileInfo
                )
            )
        }
        add(
            VideoMenuAction(
                id = "delete",
                title = "从收藏夹删除",
                icon = AppIcons.Delete,
                isDangerous = true,
                onClick = { onDelete(item.id) }
            )
        )
    }

    VideoActionBottomSheet(
        title = item.title,
        actions = actions,
        onDismissRequest = onDismissRequest
    )
}

/**
 * 新建子收藏夹弹窗
 */
@Composable
fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderNameInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        onConfirm(folderNameInput.trim())
                    }
                }
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 重命名收藏夹弹窗
 */
@Composable
fun RenameFolderDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var renameFolderNameInput by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        onConfirm(renameFolderNameInput.trim())
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 重命名视频标题弹窗
 */
@Composable
fun RenameItemDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var renameItemTitleInput by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        onConfirm(renameItemTitleInput.trim())
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
