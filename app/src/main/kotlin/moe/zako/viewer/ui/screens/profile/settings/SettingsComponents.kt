package moe.zako.viewer.ui.screens.profile.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.drawable.toBitmap
import moe.zako.viewer.core.external.AppItem
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.SettingsTokens
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget

/**
 * 设置主菜单分类条目组件
 */
@Composable
fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Lg, vertical = Spacing.Expanded),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(CornerRadius.Intermediate),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(SettingsTokens.CategoryIconContainerSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(IconSize.Md)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.Lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.Xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!trailingText.isNullOrEmpty()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(Spacing.Compact))
            }

            Icon(
                imageVector = AppIcons.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(IconSize.Sm)
            )
        }
    }
}

/**
 * 设置通用容器卡片
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(CornerRadius.Md),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Lg),
            content = content
        )
    }
}

/**
 * 通用开关条目（标题 + 副标题 + Switch）
 */
@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Spacing.Xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Spacing.Md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 自定义外部应用（播放器 / 下载器）配置卡片
 */
@Composable
fun ExternalAppCard(
    title: String,
    actionIcon: ImageVector,
    description: String,
    selectedPackage: String,
    selectedApp: AppItem?,
    defaultFallbackIcon: ImageVector,
    onOpenPicker: () -> Unit,
    onClearPackage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(CornerRadius.Md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.Lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onOpenPicker) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = "选择应用",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.Intermediate))

            Surface(
                shape = RoundedCornerShape(CornerRadius.Sm),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPicker() }
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = remember(selectedApp?.icon) {
                        try {
                            selectedApp?.icon?.toBitmap(96, 96)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(TouchTarget.Dense)
                                .clip(RoundedCornerShape(CornerRadius.Sm))
                        )
                    } else {
                        Icon(
                            imageVector = defaultFallbackIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TouchTarget.Dense)
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.Md))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedApp?.appName ?: if (selectedPackage.isEmpty()) "系统默认选择器" else selectedPackage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = selectedPackage.ifEmpty { "每次唤起时由系统弹窗选择" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (selectedPackage.isNotEmpty()) {
                        IconButton(onClick = onClearPackage) {
                            Icon(imageVector = AppIcons.Clear, contentDescription = "清除")
                        }
                    } else {
                        TextButton(onClick = onOpenPicker) {
                            Text("选择")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 存储权限引导弹窗
 */
@Composable
fun StoragePermissionGuideDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("需要「所有文件访问权限」") },
        text = {
            Text(
                text = "自定义下载目录需要系统授予「所有文件访问权限」，以便在手机外部存储或 SD 卡中自由读写文件。点击「前往设置」开启该权限。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("前往设置")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不开启")
            }
        }
    )
}
