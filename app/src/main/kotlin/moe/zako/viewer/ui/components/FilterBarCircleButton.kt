package moe.zako.viewer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import moe.zako.viewer.ui.theme.FilterBarTokens

/**
 * 筛选栏统一圆形操作图标按钮
 * 统一「筛选」「排序」「切换布局」等操作按钮的尺寸、着色机制与激活态高亮
 *
 * - 默认态：Material 3 Tonal 逻辑（secondaryContainer 背景 + onSecondaryContainer 图标）
 * - 激活态：高对比主题激活态（primary 背景 + onPrimary 图标）
 */
@Composable
fun FilterBarCircleButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(FilterBarTokens.ButtonSize),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(FilterBarTokens.IconSize),
            tint = contentColor
        )
    }
}
