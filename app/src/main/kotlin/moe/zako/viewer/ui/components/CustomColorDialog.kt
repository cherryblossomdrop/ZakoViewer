package moe.zako.viewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.CornerRadius
import moe.zako.viewer.ui.theme.DialogTokens
import moe.zako.viewer.ui.theme.IconSize
import moe.zako.viewer.ui.theme.PlayerOverlayTokens
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.TouchTarget

@Composable
fun CustomColorDialog(
    initialColorHex: String = "#686BDD",
    onDismiss: () -> Unit,
    onSaveAndApply: (String) -> Unit
) {
    // Parse initial color to HSV
    val initialHsv = remember(initialColorHex) {
        hexToHsv(initialColorHex) ?: floatArrayOf(238f, 0.53f, 0.87f)
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(hsvToHex(initialHsv[0], initialHsv[1], initialHsv[2])) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: HEX, 1: 调色板
    var isHexError by remember { mutableStateOf(false) }

    val currentColor = remember(hue, saturation, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("自定义主题色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.Xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.Expanded)
            ) {
                // 1. 实时预览色块与代码
                Card(
                    shape = RoundedCornerShape(CornerRadius.Md),
                    colors = CardDefaults.cardColors(containerColor = currentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DialogTokens.ColorPreviewCardHeight)
                        .border(BorderWidth.Thin, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(CornerRadius.Md))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(CornerRadius.Sm),
                            color = PlayerOverlayTokens.Background.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Xs)
                        ) {
                            Text(
                                text = hexInput.uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xxs)
                            )
                        }
                    }
                }

                // 2. 模式切换 Tab (HEX 输入 / 可视化调色板)
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("HEX 输入") },
                        icon = { Icon(AppIcons.Numbers, contentDescription = null, modifier = Modifier.size(IconSize.Intermediate)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("调色板") },
                        icon = { Icon(AppIcons.ColorLens, contentDescription = null, modifier = Modifier.size(IconSize.Intermediate)) }
                    )
                }

                if (selectedTab == 0) {
                    // --- HEX 输入模式 ---
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Compact)) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { input ->
                                hexInput = input
                                val clean = input.trim().removePrefix("#")
                                if (clean.length == 6 && clean.matches(Regex("^[0-9a-fA-F]{6}$"))) {
                                    isHexError = false
                                    val parsedHsv = hexToHsv("#$clean")
                                    if (parsedHsv != null) {
                                        hue = parsedHsv[0]
                                        saturation = parsedHsv[1]
                                        value = parsedHsv[2]
                                    }
                                } else {
                                    isHexError = clean.isNotEmpty()
                                }
                            },
                            label = { Text("十六进制颜色代码") },
                            placeholder = { Text("#RRGGBB") },
                            isError = isHexError,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isHexError) {
                            Text(
                                text = "请输入合法的 6 位十六进制代码 (如 #686BDD)",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Text(
                                text = "支持直接输入带 # 或不带 # 的 6 位十六进制代码",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else {
                    // --- 调色板模式 (Canvas HSV 拾色器) ---
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Intermediate)) {
                        Text(
                            text = "拖动拾取饱和度与明度：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 2D 饱和度/明度 Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DialogTokens.ColorCanvasHeight)
                                .clip(RoundedCornerShape(CornerRadius.Intermediate))
                                .border(BorderWidth.Thin, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius.Intermediate))
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(hue) {
                                        detectTapGestures { offset ->
                                            saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                            value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                            hexInput = hsvToHex(hue, saturation, value)
                                            isHexError = false
                                        }
                                    }
                                    .pointerInput(hue) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                                value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                                hexInput = hsvToHex(hue, saturation, value)
                                                isHexError = false
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                                value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                                hexInput = hsvToHex(hue, saturation, value)
                                                isHexError = false
                                            }
                                        )
                                    }
                            ) {
                                val pureColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                                drawRect(
                                    brush = Brush.horizontalGradient(listOf(Color.White, pureColor))
                                )
                                drawRect(
                                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                                )
                                val selX = saturation * size.width
                                val selY = (1f - value) * size.height
                                drawCircle(
                                    color = Color.White,
                                    radius = Spacing.Intermediate.toPx(),
                                    center = Offset(selX, selY),
                                    style = Stroke(width = BorderWidth.ColorPickerStroke.toPx())
                                )
                                drawCircle(
                                    color = currentColor,
                                    radius = BorderWidth.ColorPickerInnerRadius.toPx(),
                                    center = Offset(selX, selY)
                                )
                            }
                        }

                        Text(
                            text = "色相调节：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1D 色相条 (0° - 360°)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TouchTarget.ExtraCompact)
                                .clip(RoundedCornerShape(CornerRadius.Sm))
                                .border(BorderWidth.Thin, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius.Sm))
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                            hexInput = hsvToHex(hue, saturation, value)
                                            isHexError = false
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                                hexInput = hsvToHex(hue, saturation, value)
                                                isHexError = false
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                                hexInput = hsvToHex(hue, saturation, value)
                                                isHexError = false
                                            }
                                        )
                                    }
                            ) {
                                val rainbow = listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                                drawRect(brush = Brush.horizontalGradient(rainbow))
                                val thumbX = (hue / 360f) * size.width
                                drawLine(
                                    color = Color.White,
                                    start = Offset(thumbX, 0f),
                                    end = Offset(thumbX, size.height),
                                    strokeWidth = Spacing.Xs.toPx()
                                )
                                drawLine(
                                    color = Color.Black,
                                    start = Offset(thumbX, 0f),
                                    end = Offset(thumbX, size.height),
                                    strokeWidth = BorderWidth.Thin.toPx()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = hexInput.trim().removePrefix("#")
                    if (clean.length == 6 && clean.matches(Regex("^[0-9a-fA-F]{6}$"))) {
                        onSaveAndApply("#${clean.uppercase()}")
                        onDismiss()
                    } else {
                        isHexError = true
                    }
                }
            ) {
                Text("保存并应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun hsvToHex(h: Float, s: Float, v: Float): String {
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun hexToHsv(hex: String): FloatArray? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val num = clean.toLongOrNull(16) ?: return null
    val r = ((num shr 16) and 0xFF).toInt()
    val g = ((num shr 8) and 0xFF).toInt()
    val b = (num and 0xFF).toInt()
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    return hsv
}
