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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import moe.zako.viewer.ui.theme.AppIcons
import moe.zako.viewer.ui.theme.MotionTokens
import moe.zako.viewer.ui.theme.SearchBarTokens

/**
 * 全应用统一搜索框组件
 * 规范胶囊圆角、尺寸、底色、图标大小与中性配色，配备带触感微动效的一键清空按钮
 * 内置严密的软键盘联动失焦机制，彻底解决退出输入或滑动内容后高亮边框残留问题
 */
@OptIn(ExperimentalLayoutApi::class)
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
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val clearButtonScale = remember { Animatable(1f) }
    val clearButtonRotation = remember { Animatable(0f) }

    var isFocused by remember { mutableStateOf(false) }

    // 监听软键盘由展开转为收起的跳变
    // 防踩坑：必须严格判定 wasImeVisible == true && !isImeVisible && isFocused
    // 严禁在刚获取焦点、键盘尚未弹起的第一帧误触发失焦
    val isImeVisible = WindowInsets.isImeVisible
    var wasImeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isImeVisible) {
        if (wasImeVisible && !isImeVisible && isFocused) {
            focusManager.clearFocus()
        }
        wasImeVisible = isImeVisible
    }

    val effectiveKeyboardActions = remember(keyboardActions, onSearch, keyboardController, focusManager) {
        KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onSearch?.invoke()
                keyboardActions.onSearch?.invoke(this)
            },
            onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
                keyboardActions.onDone?.invoke(this)
            },
            onGo = {
                keyboardController?.hide()
                focusManager.clearFocus()
                keyboardActions.onGo?.invoke(this)
            },
            onSend = {
                keyboardController?.hide()
                focusManager.clearFocus()
                keyboardActions.onSend?.invoke(this)
            },
            onPrevious = keyboardActions.onPrevious,
            onNext = keyboardActions.onNext
        )
    }

    val baseModifier = modifier.onFocusChanged { focusState ->
        isFocused = focusState.isFocused
        onFocusChanged?.invoke(focusState.isFocused)
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
                enter = fadeIn(animationSpec = tween(durationMillis = MotionTokens.Duration.Compact)) +
                    scaleIn(initialScale = 0.8f, animationSpec = tween(durationMillis = MotionTokens.Duration.Compact)),
                exit = fadeOut(animationSpec = tween(durationMillis = MotionTokens.Duration.Fast)) +
                    scaleOut(targetScale = 0.8f, animationSpec = tween(durationMillis = MotionTokens.Duration.Fast))
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            launch {
                                clearButtonScale.animateTo(
                                    targetValue = 0.72f,
                                    animationSpec = tween(
                                        durationMillis = MotionTokens.Duration.Instant,
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
        keyboardActions = effectiveKeyboardActions
    )
}

/**
 * 列表滑动时自动收起软键盘并清除焦点连接
 */
@Composable
fun rememberClearFocusOnScrollConnection(): NestedScrollConnection {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 当用户通过手势拖拽滑动且产生有效位移时收起输入法与焦点
                if (source == NestedScrollSource.Drag && (available.x != 0f || available.y != 0f)) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
}

/**
 * 为容器附加滑动即收起软键盘并失焦能力的 Modifier
 */
@Composable
fun Modifier.clearFocusOnScroll(): Modifier {
    val connection = rememberClearFocusOnScrollConnection()
    return this.nestedScroll(connection)
}

/**
 * 点击外部非输入区域时收起软键盘并清除焦点的 Modifier
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return this.pointerInput(focusManager, keyboardController) {
        detectTapGestures(onTap = {
            keyboardController?.hide()
            focusManager.clearFocus()
        })
    }
}
