package moe.zako.viewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.MenuTokens

/**
 * ============================================================================
 * 视频操作菜单架构规范（Zygote 模式）
 * ============================================================================
 * 本文件遵循「全局基座 + 按需做减法（Zygote 模式）」，对外仅暴露 [StandardVideoActionMenu]
 * 与 [StandardVideoActionDropdown]，严禁外部业务页面直接拼装裸列表通用菜单项。
 *
 * 全局渲染顺序由 [BaseVideoMenuAction] 严格锁死：
 * 1. [BaseVideoMenuAction.BATCH_SELECT] (批量多选)
 * 2. [BaseVideoMenuAction.ADD_TO_FAVORITE] (加入收藏)
 * 3. [BaseVideoMenuAction.WATCH_LATER] (稍后再看)
 * 4. [BaseVideoMenuAction.VIDEO_DETAIL] (视频详情)
 * 5. [BaseVideoMenuAction.FILE_INFO] (文件信息，含副标题「查看路径、大小与本地状态」)
 *
 * 各业务页通过 `excluded` 参数做减法剔除不适用的通用项，通过 `extraActions` 插入专属插槽，
 * 通过 `destructiveAction` 挂载沉底红色危险项。
 * ============================================================================
 */

/**
 * 全局基础视频操作菜单枚举
 * 严格锁死全局通用菜单项的渲染顺序
 */
enum class BaseVideoMenuAction {
    BATCH_SELECT,     // 批量多选
    ADD_TO_FAVORITE,  // 加入收藏
    WATCH_LATER,      // 稍后再看
    VIDEO_DETAIL,     // 视频详情
    FILE_INFO         // 文件信息（含副标题「查看路径、大小与本地状态」）
}

/**
 * 视频操作菜单项定义（供页面专属插槽与危险区扩展使用）
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
 * 下拉菜单场景下默认剔除的单条专属项
 */
private val DEFAULT_DROPDOWN_EXCLUDED = setOf(
    BaseVideoMenuAction.BATCH_SELECT,
    BaseVideoMenuAction.VIDEO_DETAIL,
    BaseVideoMenuAction.FILE_INFO
)

/**
 * 内部通用的三段式菜单项构建函数（顺序严格锁死，封死在内部）
 */
private fun buildVideoMenuActions(
    excluded: Set<BaseVideoMenuAction>,
    onBatchSelect: () -> Unit = {},
    onAddToFavorite: () -> Unit = {},
    onAddToWatchLater: () -> Unit = {},
    onVideoDetail: () -> Unit = {},
    onFileInfo: (() -> Unit)? = null,
    extraActions: List<VideoMenuAction> = emptyList(),
    destructiveAction: VideoMenuAction? = null
): List<VideoMenuAction> = buildList {
    // 1. 通用基座区（顺序严格锁死）
    for (action in BaseVideoMenuAction.entries) {
        if (action in excluded) continue
        when (action) {
            BaseVideoMenuAction.BATCH_SELECT -> {
                add(
                    VideoMenuAction(
                        id = "batch_select",
                        title = "批量多选",
                        icon = AppIcons.MultiSelect,
                        onClick = onBatchSelect
                    )
                )
            }
            BaseVideoMenuAction.ADD_TO_FAVORITE -> {
                add(
                    VideoMenuAction(
                        id = "add_to_favorite",
                        title = "加入收藏",
                        icon = AppIcons.AddToFavorites,
                        onClick = onAddToFavorite
                    )
                )
            }
            BaseVideoMenuAction.WATCH_LATER -> {
                add(
                    VideoMenuAction(
                        id = "watch_later",
                        title = "稍后再看",
                        icon = AppIcons.WatchLater,
                        onClick = onAddToWatchLater
                    )
                )
            }
            BaseVideoMenuAction.VIDEO_DETAIL -> {
                add(
                    VideoMenuAction(
                        id = "video_detail",
                        title = "视频详情",
                        icon = AppIcons.VideoDetail,
                        onClick = onVideoDetail
                    )
                )
            }
            BaseVideoMenuAction.FILE_INFO -> {
                if (onFileInfo != null) {
                    add(
                        VideoMenuAction(
                            id = "file_info",
                            title = "文件信息",
                            icon = AppIcons.FileInfo,
                            supportingText = "查看路径、大小与本地状态",
                            onClick = onFileInfo
                        )
                    )
                }
            }
        }
    }
    // 2. 页面专属插槽
    addAll(extraActions)
    // 3. 底部危险区
    if (destructiveAction != null) {
        add(destructiveAction)
    }
}

/**
 * 全局标准视频操作菜单基座组件（长按底部抽屉，Zygote 模式暴露入口）
 *
 * @param title 菜单顶部展示的视频/项目标题
 * @param onDismissRequest 关闭/折叠菜单回调
 * @param modifier 外部修饰符
 * @param excluded 通用基座区中按需剔除的菜单项集合，默认为空（全量继承）
 * @param onBatchSelect 「批量多选」点击回调
 * @param onAddToFavorite 「加入收藏」点击回调
 * @param onAddToWatchLater 「稍后再看」点击回调
 * @param onVideoDetail 「视频详情」点击回调
 * @param onFileInfo 「文件信息」点击回调（若为 null 则自动忽略此项，仅在提供且未被 excluded 时展示）
 * @param extraActions 页面专属业务插槽列表（紧随通用基座区之后展示）
 * @param destructiveAction 底部危险操作（固定沉底渲染红色高亮文字与图标）
 */
@Composable
fun StandardVideoActionMenu(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    excluded: Set<BaseVideoMenuAction> = emptySet(),
    onBatchSelect: () -> Unit = {},
    onAddToFavorite: () -> Unit = {},
    onAddToWatchLater: () -> Unit = {},
    onVideoDetail: () -> Unit = {},
    onFileInfo: (() -> Unit)? = null,
    extraActions: List<VideoMenuAction> = emptyList(),
    destructiveAction: VideoMenuAction? = null
) {
    val actions = buildVideoMenuActions(
        excluded = excluded,
        onBatchSelect = onBatchSelect,
        onAddToFavorite = onAddToFavorite,
        onAddToWatchLater = onAddToWatchLater,
        onVideoDetail = onVideoDetail,
        onFileInfo = onFileInfo,
        extraActions = extraActions,
        destructiveAction = destructiveAction
    )

    VideoActionBottomSheet(
        title = title,
        actions = actions,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

/**
 * 全局标准视频操作下拉菜单基座组件（供多选顶栏右上角等场景复用，Zygote 模式暴露入口）
 *
 * @param expanded 下拉菜单是否展开
 * @param onDismissRequest 关闭/折叠菜单回调
 * @param modifier 外部修饰符
 * @param excluded 额外剔除的菜单项集合（内部已默认剔除 BATCH_SELECT、VIDEO_DETAIL、FILE_INFO 单条专属项）
 * @param onAddToFavorite 「加入收藏」点击回调
 * @param onAddToWatchLater 「稍后再看」点击回调
 * @param extraActions 页面专属业务插槽列表
 * @param destructiveAction 底部危险操作（红色高亮）
 */
@Composable
fun StandardVideoActionDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    excluded: Set<BaseVideoMenuAction> = emptySet(),
    onAddToFavorite: () -> Unit = {},
    onAddToWatchLater: () -> Unit = {},
    extraActions: List<VideoMenuAction> = emptyList(),
    destructiveAction: VideoMenuAction? = null
) {
    val effectiveExcluded = DEFAULT_DROPDOWN_EXCLUDED + excluded
    val actions = buildVideoMenuActions(
        excluded = effectiveExcluded,
        onAddToFavorite = onAddToFavorite,
        onAddToWatchLater = onAddToWatchLater,
        extraActions = extraActions,
        destructiveAction = destructiveAction
    )

    VideoActionDropdownMenu(
        expanded = expanded,
        actions = actions,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

/**
 * 底层接收裸列表的下拉菜单渲染实现函数（设为 private 封死绕过路径，杜绝外部业务页手动拼装）
 */
@Composable
private fun VideoActionDropdownMenu(
    expanded: Boolean,
    actions: List<VideoMenuAction>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        actions.forEach { action ->
            val iconTint = if (action.isDangerous) MenuTokens.DangerColor else MenuTokens.IconDefaultTint
            val textColor = if (action.isDangerous) MenuTokens.DangerColor else MaterialTheme.colorScheme.onSurface

            DropdownMenuItem(
                text = {
                    Text(
                        text = action.title,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(MenuTokens.IconSize)
                    )
                },
                onClick = {
                    onDismissRequest()
                    action.onClick()
                }
            )
        }
    }
}

/**
 * 底层接收裸列表的渲染实现函数（设为 private 封死绕过路径，杜绝外部业务页手动拼装）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoActionBottomSheet(
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
