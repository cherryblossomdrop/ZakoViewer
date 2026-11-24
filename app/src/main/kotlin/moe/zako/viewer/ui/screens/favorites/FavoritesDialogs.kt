package moe.zako.viewer.ui.screens.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.data.local.entities.FavoriteItemEntity
import moe.zako.viewer.ui.components.BaseVideoMenuAction
import moe.zako.viewer.ui.components.StandardVideoActionMenu
import moe.zako.viewer.ui.components.VideoMenuAction
import moe.zako.viewer.ui.theme.AppIcons

/**
 * 子收藏夹长按操作抽屉 (基于 StandardVideoActionMenu 与 MenuTokens)
 */
@Composable
fun FolderActionBottomSheet(
    folder: FavoriteFolderEntity,
    onStartSelection: (Long) -> Unit,
    onRename: (FavoriteFolderEntity) -> Unit,
    onMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    StandardVideoActionMenu(
        title = folder.name,
        onDismissRequest = onDismissRequest,
        excluded = BaseVideoMenuAction.entries.toSet(),
        extraActions = listOf(
            VideoMenuAction(
                id = "multi_select",
                title = "批量多选",
                icon = AppIcons.MultiSelect,
                onClick = { onStartSelection(folder.id) }
            ),
            VideoMenuAction(
                id = "rename",
                title = "修改名称",
                icon = AppIcons.Edit,
                onClick = { onRename(folder) }
            ),
            VideoMenuAction(
                id = "move",
                title = "移动目录",
                icon = AppIcons.Move,
                onClick = { onMove(folder.id) }
            )
        ),
        destructiveAction = VideoMenuAction(
            id = "delete",
            title = "删除目录",
            icon = AppIcons.Delete,
            isDangerous = true,
            onClick = { onDelete(folder.id) }
        )
    )
}

/**
 * 收藏视频条目长按操作抽屉 (基于 StandardVideoActionMenu Zygote 基座)
 */
@Composable
fun FavoriteItemActionBottomSheet(
    item: FavoriteItemEntity,
    onStartSelection: (Long) -> Unit,
    onRename: (FavoriteItemEntity) -> Unit,
    onMove: (Long) -> Unit,
    onCopy: (FavoriteItemEntity) -> Unit,
    onAddToWatchLater: (FavoriteItemEntity) -> Unit,
    onDetail: (FavoriteItemEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissRequest: () -> Unit,
    hasLocalFile: Boolean = false,
    onShowFileInfo: (() -> Unit)? = null
) {
    StandardVideoActionMenu(
        title = item.title,
        onDismissRequest = onDismissRequest,
        excluded = setOf(BaseVideoMenuAction.ADD_TO_FAVORITE),
        onBatchSelect = { onStartSelection(item.id) },
        onAddToWatchLater = { onAddToWatchLater(item) },
        onVideoDetail = { onDetail(item) },
        onFileInfo = if (hasLocalFile) onShowFileInfo else null,
        extraActions = listOf(
            VideoMenuAction(
                id = "rename",
                title = "修改名称",
                icon = AppIcons.Edit,
                onClick = { onRename(item) }
            ),
            VideoMenuAction(
                id = "move",
                title = "移动视频",
                icon = AppIcons.Move,
                onClick = { onMove(item.id) }
            ),
            VideoMenuAction(
                id = "copy",
                title = "复制视频",
                icon = AppIcons.Copy,
                onClick = { onCopy(item) }
            )
        ),
        destructiveAction = VideoMenuAction(
            id = "delete",
            title = "移出收藏",
            icon = AppIcons.Delete,
            isDangerous = true,
            onClick = { onDelete(item.id) }
        )
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
