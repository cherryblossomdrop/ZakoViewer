package moe.zako.viewer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import moe.zako.viewer.core.external.AppItem
import moe.zako.viewer.core.external.AppPickerHelper
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.DialogTokens
import moe.zako.viewer.ui.theme.FontSizes
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget

@Composable
fun AppPickerDialog(
    title: String,
    currentPackage: String,
    isVideoPlayerMode: Boolean,
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = 推荐相关, 1 = 全部应用

    var recommendedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val rec = if (isVideoPlayerMode) {
            AppPickerHelper.getVideoPlayers(context)
        } else {
            AppPickerHelper.getDownloaders(context)
        }
        val all = AppPickerHelper.getAllInstalledApps(context)
        recommendedApps = rec
        allApps = all
        isLoading = false
    }

    val activeList = if (selectedTab == 0) recommendedApps else allApps
    val filteredList = remember(activeList, searchQuery) {
        if (searchQuery.isBlank()) {
            activeList
        } else {
            val q = searchQuery.trim().lowercase()
            activeList.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearFocusOnTap()
            ) {
                // Search Field (复用全局统一搜索框)
                ZakoSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.Sm))

                // Tabs: 推荐应用 vs 全部应用
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("推荐应用 (${recommendedApps.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("全部应用 (${allApps.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.Sm))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DialogTokens.LoadingBoxHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = DialogTokens.AppPickerMaxHeight)
                            .clearFocusOnScroll(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
                    ) {
                        // 1. Reset / Default option: 系统默认选择器
                        item {
                            Surface(
                                shape = RoundedCornerShape(CornerRadius.Sm),
                                color = if (currentPackage.isEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAppSelected("")
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.Intermediate),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Apps,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(TouchTarget.Dense)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.Md))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "系统默认应用选择器",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "每次唤起时由系统弹窗选择应用",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    RadioButton(
                                        selected = currentPackage.isEmpty(),
                                        onClick = {
                                            onAppSelected("")
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Xs))
                        }

                        if (filteredList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.Xl),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedTab == 0) "未检测到已安装的相关应用，可切换至「全部应用」浏览" else "未找到匹配应用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(filteredList, key = { it.packageName }) { app ->
                                val isSelected = app.packageName == currentPackage
                                Surface(
                                    shape = RoundedCornerShape(CornerRadius.Sm),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAppSelected(app.packageName)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.Sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // App Icon
                                        val iconBitmap = remember(app.icon) {
                                            try {
                                                app.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap,
                                                contentDescription = app.appName,
                                                modifier = Modifier
                                                    .size(TouchTarget.Dense)
                                                    .clip(RoundedCornerShape(CornerRadius.Sm))
                                            )
                                        } else {
                                            Icon(
                                                imageVector = AppIcons.Apps,
                                                contentDescription = app.appName,
                                                modifier = Modifier.size(TouchTarget.Dense),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(Spacing.Md))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = FontSizes.Badge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                onAppSelected(app.packageName)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
