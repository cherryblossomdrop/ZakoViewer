package moe.zako.viewer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.SearchBarTokens

/**
 * 全应用统一搜索框组件
 * 规范胶囊圆角、尺寸、底色、图标大小与中性配色，并配备带触感微动效的一键清空按钮
 */
@Composable
fun ZakoSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String? = null,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions: KeyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() })
) {
    val coroutineScope = rememberCoroutineScope()
    val clearButtonScale = remember { Animatable(1f) }
    val clearButtonRotation = remember { Animatable(0f) }

    val baseModifier = if (onFocusChanged != null) {
        modifier.onFocusChanged { onFocusChanged(it.isFocused) }
    } else {
        modifier
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = baseModifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = SearchBarTokens.ContainerShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        leadingIcon = leadingIcon ?: {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = "搜索",
                modifier = Modifier.size(SearchBarTokens.IconSize),
                tint = SearchBarTokens.IconTint
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(durationMillis = 150)),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                    scaleOut(targetScale = 0.8f, animationSpec = tween(durationMillis = 120))
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            launch {
                                clearButtonScale.animateTo(
                                    targetValue = 0.72f,
                                    animationSpec = tween(
                                        durationMillis = 70,
                                        easing = SearchBarTokens.ClearAnimEasing
                                    )
                                )
                                clearButtonScale.animateTo(
                                    targetValue = 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                            launch {
                                clearButtonRotation.animateTo(
                                    targetValue = -90f,
                                    animationSpec = tween(
                                        durationMillis = SearchBarTokens.ClearAnimDurationMs,
                                        easing = SearchBarTokens.ClearAnimEasing
                                    )
                                )
                                clearButtonRotation.snapTo(0f)
                            }
                        }
                        onValueChange("")
                        onClear?.invoke()
                    },
                    modifier = Modifier
                        .size(SearchBarTokens.ActionButtonSize)
                        .graphicsLayer {
                            scaleX = clearButtonScale.value
                            scaleY = clearButtonScale.value
                            rotationZ = clearButtonRotation.value
                        }
                ) {
                    Icon(
                        imageVector = AppIcons.Clear,
                        contentDescription = "清空搜索内容",
                        modifier = Modifier.size(SearchBarTokens.IconSize),
                        tint = SearchBarTokens.IconTint
                    )
                }
            }
        },
        placeholder = placeholderText?.let {
            {
                Text(
                    text = it,
                    color = SearchBarTokens.PlaceholderColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}
