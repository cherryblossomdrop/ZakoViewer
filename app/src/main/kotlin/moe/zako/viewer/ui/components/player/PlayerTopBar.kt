package moe.zako.viewer.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing

/**
 * 全屏播放与详情页内嵌播放通用的顶部控制栏
 * 严格复用 PlayerControlDefaults 几何规范与 AppIcons 设计令牌
 */
@Composable
fun PlayerTopBar(
    title: String,
    onBack: () -> Unit,
    aspectRatioLabel: String,
    onCycleAspectRatio: () -> Unit,
    showMoreMenu: Boolean,
    onShowMoreMenu: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLocalPlayback: Boolean = false,
    showOrientationToggle: Boolean = false,
    onToggleOrientation: (() -> Unit)? = null,
    moreMenuItems: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PlayerOverlayTokens.Background.copy(alpha = 0.85f), Color.Transparent)
                )
            )
            .padding(horizontal = Spacing.Sm, vertical = Spacing.Compact)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                )
            }

            // 视频标题与本地标识
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = PlayerControlDefaults.ControlSpacing)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isLocalPlayback) {
                    Spacer(modifier = Modifier.width(Spacing.Compact))
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        Icon(
                            imageVector = AppIcons.OfflinePin,
                            contentDescription = "本地视频",
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                        )
                    }
                }
            }

            // 画面比例切换 (适应屏幕、填充、16:9、4:3)
            IconButton(
                onClick = onCycleAspectRatio,
                modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
            ) {
                Icon(
                    imageVector = AppIcons.AspectRatio,
                    contentDescription = "画面比例 ($aspectRatioLabel)",
                    tint = Color.White,
                    modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                )
            }

            // 屏幕方向旋转 (横竖屏切换) - 斜向异形图标居中盒模型补偿至 21.dp
            if (showOrientationToggle && onToggleOrientation != null) {
                IconButton(
                    onClick = onToggleOrientation,
                    modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                ) {
                    Box(
                        modifier = Modifier.size(PlayerControlDefaults.ControlIconSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.ScreenRotation,
                            contentDescription = "旋转屏幕",
                            tint = Color.White,
                            modifier = Modifier.size(PlayerControlDefaults.CompensatedIconSize)
                        )
                    }
                }
            }

            // “更多选项” 菜单 (⋮)
            Box {
                IconButton(
                    onClick = { onShowMoreMenu(true) },
                    modifier = Modifier.size(PlayerControlDefaults.ControlButtonSize)
                ) {
                    Icon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "更多选项",
                        tint = Color.White,
                        modifier = Modifier.size(PlayerControlDefaults.ControlIconSize)
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { onShowMoreMenu(false) },
                    content = moreMenuItems
                )
            }
        }
    }
}
