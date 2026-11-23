package moe.zako.viewer.ui.screens.profile.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import moe.zako.viewer.data.repository.SettingsRepository
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isUnlocked by settingsRepository.longPressShareUnlockedFlow.collectAsState(initial = false)

    val versionName = remember {
        moe.zako.viewer.BuildConfig.VERSION_NAME
    }

    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }

    fun onVersionClicked() {
        val now = System.currentTimeMillis()
        if (isUnlocked) {
            // 若已解锁，再次点击不再重复触发或提示已解锁
            return
        }
        if (now - lastClickTime < 1200L) {
            clickCount++
        } else {
            clickCount = 1
        }
        lastClickTime = now

        if (clickCount >= 5) {
            scope.launch {
                settingsRepository.setLongPressShareUnlocked(true)
            }
            ZakoToast.show("已解锁隐藏分享设置")
            clickCount = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.SectionSpacing)
        ) {
            // App Branding & Version
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.Xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ZakoViewer",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(Spacing.Compact))
                        Surface(
                            shape = RoundedCornerShape(CornerRadius.Sm),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onVersionClicked() }
                        ) {
                            Text(
                                text = "版本 v$versionName",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Compact)
                            )
                        }
                    }
                }
            }

            // 区块 1: 开源与许可 (包含开源组件许可声明与开源仓库，MD3 Expressive 分组与同级窄间距)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "开源与许可",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = CornerRadius.Md,
                            topEnd = CornerRadius.Md,
                            bottomStart = CornerRadius.Xs,
                            bottomEnd = CornerRadius.Xs
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(Spacing.Lg)) {
                            Text(
                                text = "开源组件许可声明",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Spacing.Sm))
                            LicenseItem("libmpv", "GPLv2 / LGPLv2.1", "Cross-platform video player core")
                            LicenseItem("NextPlayerCloud / NeoPlayer", "GPLv3", "Android MPV playback architecture & JNI bindings")
                            LicenseItem("Jetpack Compose & Material 3", "Apache-2.0", "Google Android UI Toolkit")
                            LicenseItem("OkHttp", "Apache-2.0", "Square Inc. HTTP client")
                            LicenseItem("Coil", "Apache-2.0", "Image loading for Android Compose")
                            LicenseItem("Room", "Apache-2.0", "Android SQLite ORM")
                        }
                    }

                    val repoUrl = "https://github.com/cherryblossomdrop/ZakoViewer"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    ZakoToast.show("未找到可用的网页浏览器")
                                }
                            },
                        shape = RoundedCornerShape(
                            topStart = CornerRadius.Xs,
                            topEnd = CornerRadius.Xs,
                            bottomStart = CornerRadius.Md,
                            bottomEnd = CornerRadius.Md
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.Lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Spacing.Lg))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "开源仓库", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "前往 GitHub 查看项目源码与版本更新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(imageVector = AppIcons.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            // 区块 2: 诊断与排错 (导出日志独立区块)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "诊断与排错",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToLogs() },
                        shape = RoundedCornerShape(CornerRadius.Md),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.Lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Spacing.Lg))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "导出日志", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "实时查看客户端运行日志，一键导出为文本文件排错",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(imageVector = AppIcons.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LicenseItem(name: String, license: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = Spacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
