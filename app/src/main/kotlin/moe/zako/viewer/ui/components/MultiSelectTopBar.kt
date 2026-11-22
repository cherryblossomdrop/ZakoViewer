package moe.zako.viewer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.ui.theme.AppIcons

/**
 * 全局统一多选态顶栏组件
 *
 * @param selectedCount 当前选中的条目总数
 * @param onExitSelection 退出多选模式回调（同时由导航返回键与左上角关闭按钮触发）
 * @param allSelected 是否已全选（用于动态显示“全选”与“取消全选”文本）
 * @param onToggleSelectAll 点击全选/反选按钮的回调
 * @param modifier 顶栏修饰符
 * @param enableBackHandler 是否自动启用系统返回键拦截并退出多选（默认开启）
 * @param actions 页面特有的批量操作按钮插槽
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    onExitSelection: () -> Unit,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    enableBackHandler: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (enableBackHandler) {
        BackHandler(enabled = true) {
            onExitSelection()
        }
    }

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onExitSelection) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "退出多选"
                )
            }
        },
        actions = {
            TextButton(onClick = onToggleSelectAll) {
                Text(if (allSelected) "取消全选" else "全选")
            }
            actions()
        }
    )
}
