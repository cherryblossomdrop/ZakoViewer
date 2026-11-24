package moe.zako.viewer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.MenuTokens

/**
 * ============================================================================
 * 批量操作菜单架构规范（Zygote 模式）
 * ============================================================================
 * 本文件遵循「全局基座 + 按需做减法（Zygote 模式）」，对外仅暴露 [StandardBatchActionMenu]，
 * 严禁外部业务页面直接拼装裸列表通用批量菜单项。
 *
 * 全局渲染顺序由 [BaseBatchMenuAction] 严格锁死：
 * 1. [BaseBatchMenuAction.ADD_TO_FAVORITE] (加入收藏)
 * 2. [BaseBatchMenuAction.ADD_TO_WATCH_LATER] (加入待看)
 *
 * 各业务页通过 `excluded` 参数做减法剔除不适用的通用项，通过 `extraActions` 插入专属插槽，
 * 通过 `destructiveAction` 挂载沉底红色危险项。
 * ============================================================================
 */

/**
 * 全局基础批量操作菜单枚举
 * 严格锁死全局通用批量菜单项的渲染顺序
 */
enum class BaseBatchMenuAction {
    ADD_TO_FAVORITE,    // 加入收藏
    ADD_TO_WATCH_LATER  // 加入待看
}

/**
 * 批量操作菜单项定义（供页面专属插槽与危险区扩展使用）
 */
data class BatchMenuAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val isDangerous: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 全局标准批量操作菜单基座组件（Zygote 模式唯一对外暴露入口）
 *
 * @param expanded 菜单是否展开
 * @param onDismissRequest 关闭/折叠菜单回调
 * @param modifier 外部修饰符
 * @param excluded 通用基座区中按需剔除的菜单项集合，默认为空（全量继承）
 * @param onAddToFavorite 「加入收藏」点击回调
 * @param onAddToWatchLater 「加入待看」点击回调
 * @param extraActions 页面专属业务插槽列表（紧随通用基座区之后展示）
 * @param destructiveAction 底部危险操作（固定沉底渲染红色高亮文字与图标）
 */
@Composable
fun StandardBatchActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    excluded: Set<BaseBatchMenuAction> = emptySet(),
    onAddToFavorite: () -> Unit = {},
    onAddToWatchLater: () -> Unit = {},
    extraActions: List<BatchMenuAction> = emptyList(),
    destructiveAction: BatchMenuAction? = null
) {
    val actions = buildList {
        // 1. 通用基座区（顺序严格锁死）
        for (action in BaseBatchMenuAction.entries) {
            if (action in excluded) continue
            when (action) {
                BaseBatchMenuAction.ADD_TO_FAVORITE -> {
                    add(
                        BatchMenuAction(
                            id = "add_to_favorite",
                            title = "加入收藏",
                            icon = AppIcons.AddToFavorites,
                            onClick = onAddToFavorite
                        )
                    )
                }
                BaseBatchMenuAction.ADD_TO_WATCH_LATER -> {
                    add(
                        BatchMenuAction(
                            id = "add_to_watch_later",
                            title = "加入待看",
                            icon = AppIcons.WatchLater,
                            onClick = onAddToWatchLater
                        )
                    )
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

    BatchActionDropdownMenu(
        expanded = expanded,
        actions = actions,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

/**
 * 底层接收裸列表的渲染实现函数（设为 private 封死绕过路径，杜绝外部业务页手动拼装）
 */
@Composable
private fun BatchActionDropdownMenu(
    expanded: Boolean,
    actions: List<BatchMenuAction>,
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
