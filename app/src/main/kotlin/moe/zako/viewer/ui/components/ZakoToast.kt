package moe.zako.viewer.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import moe.zako.viewer.ui.theme.BorderWidth
import moe.zako.viewer.ui.theme.Elevation
import moe.zako.viewer.ui.theme.Spacing
import moe.zako.viewer.ui.theme.ToastTokens
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data model for in-app toast
 */
data class ZakoToastData(
    val id: Long = System.nanoTime(),
    val message: String,
    val durationMs: Long
)

/**
 * Global singleton manager for Material 3 Expressive (MD3E) theme-aware in-app toasts.
 * Replaces system toasts (which show app launcher icons on Android 12+) with
 * a refined, floating capsule pill adhering to MD3 Expressive standards and dynamic color schemes.
 */
object ZakoToast {
    private val _currentToast = MutableStateFlow<ZakoToastData?>(null)
    val currentToast: StateFlow<ZakoToastData?> = _currentToast.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dismissJob: Job? = null

    /**
     * Show a Material 3 Expressive theme-aware toast.
     *
     * @param message Text to display
     * @param isLong Whether to display for an extended duration (3500ms vs 2000ms)
     */
    fun show(message: String, isLong: Boolean = false) {
        if (message.isBlank()) return
        scope.launch {
            dismissJob?.cancel()
            val durationMs = if (isLong) 3500L else 2000L
            val data = ZakoToastData(message = message, durationMs = durationMs)
            _currentToast.value = data
            dismissJob = launch {
                delay(durationMs)
                if (_currentToast.value?.id == data.id) {
                    _currentToast.value = null
                }
            }
        }
    }

    /**
     * Compatibility overload matching standard Toast.makeText signature.
     * context is accepted for drop-in replacement convenience.
     */
    fun show(context: Context? = null, message: String, duration: Int = 0) {
        show(message, isLong = duration != 0)
    }

    /**
     * Immediately dismiss any currently active toast.
     */
    fun dismiss() {
        scope.launch {
            dismissJob?.cancel()
            _currentToast.value = null
        }
    }
}

/**
 * Material 3 Expressive (MD3E) Theme-Aware Toast Host Composable.
 * Renders the floating capsule pill with smooth spring & fade motion.
 * Positioned cleanly above bottom navigation without intercepting underlying screen touches.
 */
@Composable
fun ZakoToastHost(
    modifier: Modifier = Modifier
) {
    val toastData by ZakoToast.currentToast.collectAsState()

    AnimatedVisibility(
        visible = toastData != null,
        modifier = modifier.wrapContentSize(Alignment.Center),
        enter = fadeIn(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
        ) + scaleOut(
            targetScale = 0.90f,
            animationSpec = tween(durationMillis = 160)
        ) + slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = tween(durationMillis = 160)
        )
    ) {
        toastData?.let { data ->
            ZakoToastPill(
                message = data.message,
                onDismiss = { ZakoToast.dismiss() }
            )
        }
    }
}

/**
 * Material 3 Expressive Theme-Aware Capsule Pill:
 * - Dynamic color binding: secondaryContainer background, onSecondaryContainer content, primary accent outline
 * - Seamless adaptation to Monet dynamic theming and user custom HEX color schemes
 * - Capsule floating elevation with subtle shadow
 * - Dismissible via gentle tap
 */
@Composable
fun ZakoToastPill(
    message: String,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)

    Surface(
        modifier = modifier
            .padding(horizontal = Spacing.Xl)
            .shadow(
                elevation = Elevation.Level3,
                shape = CircleShape,
                clip = false,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .border(
                width = BorderWidth.Thin,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = Elevation.Level3
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = ToastTokens.MinWidth, max = ToastTokens.MaxWidth)
                .padding(horizontal = ToastTokens.HorizontalPadding, vertical = ToastTokens.VerticalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
