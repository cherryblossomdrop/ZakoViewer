package moe.zako.viewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import moe.zako.viewer.data.local.entities.FavoriteFolderEntity
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.DialogTokens
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing

@Composable
fun FolderTreeDialog(
    folders: List<FavoriteFolderEntity>,
    onDismiss: () -> Unit,
    onFolderSelected: (folderId: Long) -> Unit,
    onCreateFolder: (name: String, parentId: Long?) -> Unit
) {
    var selectedFolderId by remember { mutableStateOf<Long?>(folders.firstOrNull()?.id) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderParentId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(folders) {
        if (selectedFolderId == null || folders.none { it.id == selectedFolderId }) {
            selectedFolderId = folders.firstOrNull()?.id
        }
    }

    // Organize folders hierarchically
    val rootFolders = remember(folders) { folders.filter { it.parentId == null } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "选择目标收藏夹", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = {
                    newFolderParentId = selectedFolderId ?: rootFolders.firstOrNull()?.id
                    showCreateDialog = true
                }) {
                    Icon(imageVector = AppIcons.Add, contentDescription = "新建收藏夹")
                }
            }
        },
        text = {
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无收藏夹")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = DialogTokens.MaxListHeight)
                ) {
                    items(rootFolders) { root ->
                        FolderTreeItem(
                            folder = root,
                            depth = 0,
                            selectedId = selectedFolderId,
                            allFolders = folders,
                            onSelect = { selectedFolderId = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedFolderId?.let { onFolderSelected(it) }
                    onDismiss()
                },
                enabled = selectedFolderId != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建子收藏夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("收藏夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val parentId = newFolderParentId ?: rootFolders.firstOrNull()?.id
                            onCreateFolder(newFolderName.trim(), parentId)
                            newFolderName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun FolderTreeItem(
    folder: FavoriteFolderEntity,
    depth: Int,
    selectedId: Long?,
    allFolders: List<FavoriteFolderEntity>,
    onSelect: (Long) -> Unit
) {
    val isSelected = folder.id == selectedId
    val children = remember(allFolders, folder.id) { allFolders.filter { it.parentId == folder.id } }

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(folder.id) },
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.Dp20 * depth + Spacing.Sm,
                        top = Spacing.Sm,
                        bottom = Spacing.Sm,
                        end = Spacing.Sm
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (depth > 0) AppIcons.Subdirectory else AppIcons.Folder,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Md)
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Intermediate)
                    )
                }
            }
        }

        // Render recursive children (infinite depth)
        for (child in children) {
            FolderTreeItem(
                folder = child,
                depth = depth + 1,
                selectedId = selectedId,
                allFolders = allFolders,
                onSelect = onSelect
            )
        }
    }
}
