package moe.zako.viewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.MenuTokens
import moe.zako.viewer.ui.theme.Spacing

/**
 * 文本操作浮窗状态承载模型
 */
private data class TextToolbarState(
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?
)

/**
 * 单个文本操作项数据模型
 */
private data class TextToolbarAction(
    val label: String,
    val onClick: () -> Unit
)

/**
 * 全局自定义文本工具栏定位算法提供器
 *
 * 智能定位策略：
 * 1. 水平定位：基于 [rect] 水平居中，并通过 [screenMarginPx] 边界夹紧防止超出屏幕左右边缘；
 * 2. 垂直定位：默认位于 [rect.top] 上方并预留 [verticalSpacingPx] 间距；
 *    若顶部空间不足（如靠近状态栏或屏幕顶部），则自动翻转至 [rect.bottom] 下方并夹紧在可视范围内。
 */
private class ZakoTextToolbarPositionProvider(
    private val rect: Rect,
    private val verticalSpacingPx: Int,
    private val screenMarginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val popupWidth = popupContentSize.width
        val popupHeight = popupContentSize.height

        // 1. 水平居中与屏幕边界夹紧
        val anchorCenterX = (rect.left + rect.right) / 2f
        val idealX = (anchorCenterX - popupWidth / 2f).roundToInt()
        val minX = screenMarginPx
        val maxX = (windowSize.width - popupWidth - screenMarginPx).coerceAtLeast(minX)
        val x = idealX.coerceIn(minX, maxX)

        // 2. 垂直定位与顶部空间不足自动翻转
        val preferredY = (rect.top - popupHeight - verticalSpacingPx).roundToInt()
        val minY = screenMarginPx
        val maxY = (windowSize.height - popupHeight - screenMarginPx).coerceAtLeast(minY)

        val y = if (preferredY >= minY) {
            preferredY.coerceIn(minY, maxY)
        } else {
            val flippedY = (rect.bottom + verticalSpacingPx).roundToInt()
            flippedY.coerceIn(minY, maxY)
        }

        return IntOffset(x, y)
    }
}

/**
 * 全局自定义文本工具栏组件
 *
 * 接管 Compose 默认的 [TextToolbar]，基于纯 Compose 渲染跟随动态主题色的文本操作浮窗（剪切/复制/粘贴/全选），
 * 替代原生 ActionMode 带来的非沉浸式冷灰/紫色样式。
 */
class ZakoTextToolbar : TextToolbar {
    private var currentState by mutableStateOf<TextToolbarState?>(null)

    override val status: TextToolbarStatus
        get() = if (currentState != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        currentState = TextToolbarState(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested
        )
    }

    override fun hide() {
        currentState = null
    }

    /**
     * 浮窗宿主组合项，需挂载在拥有 [MaterialTheme] 上下文的根容器中
     */
    @Composable
    fun Content() {
        val state = currentState ?: return

        // 严格按照「剪切 -> 复制 -> 粘贴 -> 全选」顺序收集非空回调
        val actions = buildList {
            if (state.onCutRequested != null) {
                add(TextToolbarAction("剪切") {
                    state.onCutRequested.invoke()
                    hide()
                })
            }
            if (state.onCopyRequested != null) {
                add(TextToolbarAction("复制") {
                    state.onCopyRequested.invoke()
                    hide()
                })
            }
            if (state.onPasteRequested != null) {
                add(TextToolbarAction("粘贴") {
                    state.onPasteRequested.invoke()
                    hide()
                })
            }
            if (state.onSelectAllRequested != null) {
                add(TextToolbarAction("全选") {
                    state.onSelectAllRequested.invoke()
                    hide()
                })
            }
        }

        if (actions.isEmpty()) return

        val density = LocalDensity.current
        val verticalSpacingPx = with(density) { Spacing.Sm.roundToPx() }
        val screenMarginPx = with(density) { Spacing.Sm.roundToPx() }

        val positionProvider = remember(state.rect, verticalSpacingPx, screenMarginPx) {
            ZakoTextToolbarPositionProvider(
                rect = state.rect,
                verticalSpacingPx = verticalSpacingPx,
                screenMarginPx = screenMarginPx
            )
        }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = { hide() },
            properties = PopupProperties(focusable = false)
        ) {
            Surface(
                shape = RoundedCornerShape(CornerRadius.Pill),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = MenuTokens.TonalElevation,
                shadowElevation = MenuTokens.ShadowElevation
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.Xs, vertical = Spacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .width(BorderWidth.Thin)
                                    .height(Spacing.Lg)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(CornerRadius.Pill))
                                .clickable(onClick = action.onClick)
                                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
