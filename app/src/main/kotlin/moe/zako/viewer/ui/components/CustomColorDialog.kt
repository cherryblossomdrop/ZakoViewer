package moe.zako.viewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 实时预览色块与代码
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = currentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = hexInput.uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                        icon = { Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("调色板") },
                        icon = { Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    // --- HEX 输入模式 ---
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "拖动拾取饱和度与明度：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 2D 饱和度/明度 Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
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
                                    radius = 10.dp.toPx(),
                                    center = Offset(selX, selY),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                                drawCircle(
                                    color = currentColor,
                                    radius = 7.5.dp.toPx(),
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
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
                                    strokeWidth = 4.dp.toPx()
                                )
                                drawLine(
                                    color = Color.Black,
                                    start = Offset(thumbX, 0f),
                                    end = Offset(thumbX, size.height),
                                    strokeWidth = 1.dp.toPx()
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
