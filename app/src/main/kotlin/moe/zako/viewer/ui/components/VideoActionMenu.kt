package moe.zako.viewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import moe.zako.viewer.ui.theme.MenuTokens

/**
 * 视频操作菜单项定义
 */
data class VideoMenuAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val supportingText: String? = null,
    val isDangerous: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 全局统一视频长按上下文操作菜单组件
 * 以离线下载长按视频菜单为视觉基准，严格通过 [MenuTokens] 驱动尺寸、圆角、内边距与危险色
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionBottomSheet(
    title: String,
    actions: List<VideoMenuAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = MenuTokens.ContainerShape,
        tonalElevation = MenuTokens.TonalElevation,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MenuTokens.SheetPadding)
                .padding(bottom = MenuTokens.SheetBottomPadding),
            verticalArrangement = Arrangement.spacedBy(MenuTokens.ItemSpacing)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = MenuTokens.TitleBottomSpacing)
            )

            actions.forEach { action ->
                val iconTint = if (action.isDangerous) MenuTokens.DangerColor else MenuTokens.IconDefaultTint
                val textColor = if (action.isDangerous) MenuTokens.DangerColor else MaterialTheme.colorScheme.onSurface

                ListItem(
                    headlineContent = {
                        Text(
                            text = action.title,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = action.supportingText?.let {
                        {
                            Text(
                                text = it,
                                fontSize = MenuTokens.SupportingTextSize,
                                color = if (action.isDangerous) MenuTokens.DangerColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(MenuTokens.IconSize)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MenuTokens.ItemShape)
                        .clickable {
                            onDismissRequest()
                            action.onClick()
                        }
                )
            }
        }
    }
}

/**
 * 语义包装组件：VideoContextMenu / VideoActionSheet 等价于 VideoActionBottomSheet
 */
@Composable
fun VideoContextMenu(
    title: String,
    actions: List<VideoMenuAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    VideoActionBottomSheet(
        title = title,
        actions = actions,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

@Composable
fun VideoActionSheet(
    title: String,
    actions: List<VideoMenuAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    VideoActionBottomSheet(
        title = title,
        actions = actions,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}
