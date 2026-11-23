package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.ui.components.CustomColorDialog
import moe.zako.viewer.ui.components.ZakoToast
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.ThemePreset
import moe.zako.viewer.ui.theme.TouchTarget
import moe.zako.viewer.ui.theme.parseHexColor

/**
 * 外观设置子页面 (莫奈动态取色 + 主题色系统)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var themeToDelete by remember { mutableStateOf<String?>(null) }

    val showRestartToast = {
        ZakoToast.show("图标设置已保存，重启应用后生效")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观设置") },
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
            // 区块 1: 应用图标
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "应用图标",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )
                    SettingsCard(shape = RoundedCornerShape(CornerRadius.CardGroup)) {
                        SettingsSwitchItem(
                            title = "图标颜色跟随应用主题色",
                            subtitle = "应用桌面图标变体将自适应跟随莫奈动态取色或自定义主题色切换（重启应用后生效）",
                            checked = state.appIconFollowTheme,
                            onCheckedChange = {
                                viewModel.setAppIconFollowTheme(it)
                                showRestartToast()
                            }
                        )
                    }
                }
            }

            // 区块 2: 主题与配色 (莫奈取色与自定义主题色分组，MD3 Expressive 同级窄间距)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.GroupItemSpacing)) {
                    Text(
                        text = "主题与配色",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.Xs, bottom = Spacing.Xs)
                    )
                    SettingsCard(
                        shape = RoundedCornerShape(
                            topStart = CornerRadius.CardGroup,
                            topEnd = CornerRadius.CardGroup,
                            bottomStart = if (!state.dynamicColor) CornerRadius.Xs else CornerRadius.CardGroup,
                            bottomEnd = if (!state.dynamicColor) CornerRadius.Xs else CornerRadius.CardGroup
                        )
                    ) {
                        SettingsSwitchItem(
                            title = "莫奈动态取色",
                            subtitle = "跟随 Android 12+ 系统壁纸色彩动态生成协调的 Material 3 配色方案",
                            checked = state.dynamicColor,
                            onCheckedChange = {
                                viewModel.setDynamicColor(it)
                                if (state.appIconFollowTheme) {
                                    showRestartToast()
                                }
                            }
                        )
                    }

                    // 主题色选择器 (当莫奈取色关闭后展示)
                    AnimatedVisibility(visible = !state.dynamicColor) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = CornerRadius.Xs,
                                topEnd = CornerRadius.Xs,
                                bottomStart = CornerRadius.CardGroup,
                                bottomEnd = CornerRadius.CardGroup
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(Spacing.Lg)) {
                                Text(
                                    text = "应用主题色",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(Spacing.Xs))
                                Text(
                                    text = "选择预置主题色或添加自定义颜色，即时生效并持久化保存：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(Spacing.Expanded))

                                val presets = ThemePreset.values()

                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                                    // 1. 系统预置 7 款颜色（严格二字命名，不可删除）
                                    presets.forEach { theme ->
                                        val isSelected = state.customTheme.equals(theme.hexColor, ignoreCase = true) ||
                                                state.customTheme.equals(theme.key, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(CornerRadius.Intermediate),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setCustomTheme(theme.hexColor)
                                                    if (state.appIconFollowTheme) {
                                                        showRestartToast()
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = Spacing.Expanded, vertical = Spacing.Md),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(IconSize.ColorCircle)
                                                        .clip(CircleShape)
                                                        .background(theme.primaryColor)
                                                        .border(BorderWidth.Thick, Color.White.copy(alpha = 0.5f), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.Expanded))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = theme.displayName,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = "${theme.hexColor} (内置预置)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = AppIcons.CheckCircle,
                                                        contentDescription = "已选择",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 2. 用户保存的自定义颜色（支持删除）
                                    val customList = state.savedCustomThemes.filter { !ThemePreset.isBuiltinPreset(it) }
                                    customList.forEach { customHex ->
                                        val isSelected = state.customTheme.equals(customHex, ignoreCase = true)
                                        val color = parseHexColor(customHex) ?: MaterialTheme.colorScheme.primary
                                        Surface(
                                            shape = RoundedCornerShape(CornerRadius.Intermediate),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setCustomTheme(customHex)
                                                    if (state.appIconFollowTheme) {
                                                        showRestartToast()
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = Spacing.Expanded, vertical = Spacing.Sm),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(IconSize.ColorCircle)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(BorderWidth.Thick, Color.White.copy(alpha = 0.5f), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.Expanded))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "自定义颜色",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = customHex,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = AppIcons.CheckCircle,
                                                        contentDescription = "已选择",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(Spacing.Sm))
                                                }
                                                IconButton(
                                                    onClick = { themeToDelete = customHex },
                                                    modifier = Modifier.size(TouchTarget.Dense)
                                                ) {
                                                    Icon(
                                                        imageVector = AppIcons.DeleteOutline,
                                                        contentDescription = "删除该自定义颜色",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. “自定义颜色” (+) 按钮入口
                                    Surface(
                                        shape = RoundedCornerShape(CornerRadius.Intermediate),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(BorderWidth.Thin, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showCustomColorDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Spacing.Expanded, vertical = Spacing.Md),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(IconSize.ColorCircle)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.Add,
                                                    contentDescription = "添加",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(IconSize.Intermediate)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(Spacing.Expanded))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "自定义主题色",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "HEX 输入或可视化调色板选色",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = AppIcons.ArrowForward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(IconSize.Xs)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initialColorHex = state.customTheme,
            onDismiss = { showCustomColorDialog = false },
            onSaveAndApply = { newHex ->
                viewModel.addCustomTheme(newHex)
                if (state.appIconFollowTheme) {
                    showRestartToast()
                }
            }
        )
    }

    if (themeToDelete != null) {
        AlertDialog(
            onDismissRequest = { themeToDelete = null },
            title = { Text("删除自定义主题色") },
            text = { Text("确定要删除自定义主题色 $themeToDelete 吗？若当前正使用该颜色，将自动回退至默认主题色。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hex = themeToDelete
                        themeToDelete = null
                        if (hex != null) {
                            viewModel.removeCustomTheme(hex)
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { themeToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}
