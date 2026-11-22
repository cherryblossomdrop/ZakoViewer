package moe.zako.viewer.ui.components.player

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 全场景通用播放倍速选择弹窗组件
 * 兼容横屏全屏、竖屏全屏及详情页竖屏内嵌等所有播放形态
 * 支持垂直滚动、动态可用视口高度约束、当前选中项自动居中定位与手势穿透隔离
 */
@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    speedOptions: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = configuration.screenHeightDp

    // 动态根据屏幕方向和可用高度计算菜单最大高度，留出上下合理边距防截断
    val maxHeight = if (isLandscape) {
        (screenHeightDp * 0.62f).coerceAtLeast(180f).coerceAtMost(260f).dp
    } else {
        (screenHeightDp * 0.45f).coerceAtLeast(240f).coerceAtMost(380f).dp
    }

    val scrollState = rememberScrollState()

    // 找到当前选中倍速对应索引
    val selectedIndex = remember(currentSpeed, speedOptions) {
        speedOptions.indexOfFirst { abs(it - currentSpeed) < 0.05f }
    }

    // 打开菜单时自动平滑滚动至当前选中项可见位置
    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0) {
            val itemHeightApproxPx = with(density) { 48.dp.toPx() }
            val targetScrollPx = ((selectedIndex - 1).coerceAtLeast(0) * itemHeightApproxPx).toInt()
            scrollState.animateScrollTo(targetScrollPx)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "选择播放倍速",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    // 消费垂直拖动手势，彻底阻断穿透到背景播放器触发亮度/音量手势
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, _ -> }
                    }
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    speedOptions.forEach { speed ->
                        val isSelected = abs(currentSpeed - speed) < 0.05f

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSpeedSelect(speed)
                                        onDismissRequest()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${speed}x",
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "当前选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("完成")
            }
        }
    )
}
