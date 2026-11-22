package moe.zako.viewer.ui.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import moe.zako.viewer.core.permission.StoragePermissionHelper
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.DialogTokens
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import java.io.File

@Composable
fun FolderBrowserDialog(
    initialPath: String = "",
    onDismiss: () -> Unit,
    onFolderSelected: (selectedAbsolutePath: String) -> Unit
) {
    val rootPath = remember {
        val emulated = File("/storage/emulated/0")
        if (emulated.exists() && emulated.canRead()) {
            emulated.absolutePath
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
    }

    var currentDir by remember {
        val initial = if (initialPath.isNotBlank()) File(initialPath) else File(rootPath)
        val valid = if (initial.exists() && initial.isDirectory) initial else File(rootPath)
        mutableStateOf(valid)
    }

    val context = LocalContext.current
    var hasStoragePermission by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    ObserveStoragePermissionOnResume { granted ->
        hasStoragePermission = granted
        refreshTrigger++
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val subFolders = remember(currentDir, refreshTrigger) {
        try {
            val list = currentDir.listFiles { f ->
                f.isDirectory && !f.name.startsWith(".")
            } ?: emptyArray()
            list.sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList<File>()
        }
    }

    val canGoUp = currentDir.absolutePath != rootPath && currentDir.parentFile != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择下载目录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    newFolderName = ""
                    showCreateDialog = true
                }) {
                    Icon(
                        imageVector = AppIcons.CreateFolder,
                        contentDescription = "新建文件夹",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Current Path Banner
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Sm),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.Intermediate, vertical = Spacing.Compact),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Sm)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Compact))
                        Text(
                            text = currentDir.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = FontSizes.Badge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!hasStoragePermission) {
                    Spacer(modifier = Modifier.height(Spacing.Compact))
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Sm),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { StoragePermissionHelper.requestAllFilesAccess(context) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.Intermediate, vertical = Spacing.Compact),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(IconSize.Sm)
                            )
                            Spacer(modifier = Modifier.width(Spacing.Compact))
                            Text(
                                text = "未获得所有文件访问权限，点击前往授权",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = FontSizes.Badge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Sm))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = DialogTokens.FolderBrowserMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
                ) {
                    // Navigate up / parent item
                    if (canGoUp) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(CornerRadius.Sm),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentDir.parentFile?.let { currentDir = it }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.Sm, vertical = Spacing.Intermediate),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppIcons.ArrowUpward,
                                        contentDescription = "返回上一级",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(IconSize.Md)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.Intermediate))
                                    Text(
                                        text = ".. (返回上一级)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Xxs))
                        }
                    }

                    if (subFolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.Xl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "当前目录下无子文件夹，可点击右上角新建",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(subFolders, key = { it.absolutePath }) { folder ->
                            Surface(
                                shape = RoundedCornerShape(CornerRadius.Sm),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentDir = folder
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.Sm, vertical = Spacing.Sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(IconSize.Lg)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.Intermediate))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val count = folder.listFiles()?.size ?: 0
                                        Text(
                                            text = "$count 项",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = FontSizes.Badge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = AppIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(IconSize.Intermediate)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onFolderSelected(currentDir.absolutePath)
                    onDismiss()
                }
            ) {
                Text("选定当前文件夹")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // New Folder Dialog
    if (showCreateDialog) {
        var createError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = {
                        newFolderName = it
                        createError = null
                    },
                    label = { Text("文件夹名称") },
                    placeholder = { Text("例如: ZakoDownloads") },
                    isError = createError != null,
                    supportingText = {
                        createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newFolderName.trim()
                        if (trimmed.isEmpty()) {
                            createError = "文件夹名称不能为空"
                            ZakoToast.show("文件夹名称不能为空")
                            return@Button
                        }
                        if (trimmed.contains(Regex("[\\\\/:*?\"<>|]"))) {
                            createError = "文件夹名称包含非法字符"
                            ZakoToast.show("文件夹名称包含非法字符")
                            return@Button
                        }
                        val newFolder = File(currentDir, trimmed)
                        if (newFolder.exists()) {
                            createError = "文件夹已存在"
                            ZakoToast.show("文件夹已存在")
                        } else {
                            val success = newFolder.mkdirs()
                            if (success) {
                                ZakoToast.show("新建成功")
                                refreshTrigger++
                                showCreateDialog = false
                            } else {
                                createError = "创建失败，请检查存储读写权限"
                                ZakoToast.show("创建失败，请检查存储读写权限")
                            }
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
