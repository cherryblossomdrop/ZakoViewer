package moe.zako.viewer.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.CornerRadius

/**
 * 首页分页排布模式跳转指定页弹窗 (Material 3 AlertDialog)
 *
 * @param currentPage 当前页码 (1-based 正整数)
 * @param totalPages 总页数上限 (若已知，未知时为 null)
 * @param onConfirm 确认跳转回调 (入参为 1-based 目标页码)
 * @param onDismiss 关闭弹窗回调
 */
@Composable
fun HomeJumpPageDialog(
    currentPage: Int,
    totalPages: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var inputPageText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {}
    }

    val submit = {
        val text = inputPageText.trim()
        if (text.isEmpty()) {
            errorMessage = "请输入目标页码"
        } else {
            val target = text.toIntOrNull()
            if (target == null || target < 1) {
                errorMessage = "页码必须为正整数 (>= 1)"
            } else if (totalPages != null && totalPages > 0 && target > totalPages) {
                errorMessage = "页码超出上限，请输入 1 ~ $totalPages"
            } else if (target == currentPage) {
                // 若输入与当前页码相同则直接关闭弹窗
                keyboardController?.hide()
                onDismiss()
            } else {
                keyboardController?.hide()
                onConfirm(target)
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.Numbers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "跳转到指定页",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = inputPageText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            inputPageText = newValue
                            if (errorMessage != null) {
                                errorMessage = null
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text("目标页码") },
                    placeholder = {
                        val placeholderText = if (totalPages != null && totalPages > 0) {
                            "$currentPage (1 ~ $totalPages)"
                        } else {
                            "$currentPage"
                        }
                        Text(placeholderText)
                    },
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (totalPages != null && totalPages > 0) {
                            Text(
                                text = "有效页码范围: 1 ~ $totalPages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "请输入要跳转的正整数页码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { submit() },
                        onDone = { submit() }
                    ),
                    shape = RoundedCornerShape(CornerRadius.Sm)
                )
            }
        },
        confirmButton = {
            Button(onClick = { submit() }) {
                Text("跳转")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(CornerRadius.Lg)
    )
}
