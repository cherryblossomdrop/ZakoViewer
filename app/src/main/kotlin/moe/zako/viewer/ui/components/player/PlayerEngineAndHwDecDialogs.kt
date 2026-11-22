package moe.zako.viewer.ui.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.core.player.HwDecMode
import moe.zako.viewer.core.player.PlayerEngine
import moe.zako.viewer.ui.theme.Spacing

/**
 * 播放内核切换弹窗 (系统 / MPV)
 */
@Composable
fun PlayerEngineDialog(
    currentEngine: PlayerEngine,
    onSelectEngine: (PlayerEngine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择播放引擎内核") },
        text = {
            Column {
                Text(
                    text = "系统内核基于 Android 原生 MediaPlayer，能耗低、稳定性高；MPV 内核支持流媒体激进缓冲、NextPlayer 调优参数及硬件解码切换：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.Md))
                PlayerEngine.values().forEach { engine ->
                    val isSelected = currentEngine == engine
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEngine(engine) }
                            .padding(vertical = Spacing.Intermediate, horizontal = Spacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectEngine(engine) }
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = engine.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

/**
 * 硬件解码设置弹窗 (NextPlayer 规范)
 */
@Composable
fun PlayerHwDecDialog(
    currentMode: HwDecMode,
    onSelectMode: (HwDecMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("硬件解码设置 (NextPlayer 规范)") },
        text = {
            Column {
                Text(
                    text = "选择视频硬件解码模式，建议开启以降低发热与提高播放流畅度：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.Md))
                HwDecMode.values().forEach { mode ->
                    val isSelected = currentMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMode(mode) }
                            .padding(vertical = Spacing.Intermediate, horizontal = Spacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectMode(mode) }
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = mode.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
