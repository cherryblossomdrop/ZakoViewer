package moe.zako.viewer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

/**
 * 全局动画与过渡设计令牌 (Motion Tokens)
 * 严格对齐 Material 3 Expressive 动效系统，规范全应用的微交互时长、缓动曲线与过渡规格
 */
object MotionTokens {
    /**
     * 语义化动效时长 (毫秒)
     */
    object Duration {
        /** 瞬间响应 (70ms) - 用于按钮微按压缩放 */
        const val Instant: Int = 70

        /** 快速动效 (120ms) - 用于组件轻量级淡出、微小图标旋转归位 */
        const val Fast: Int = 120

        /** 紧凑动效 (150ms) - 用于次级浮层展开、搜索框清空按钮淡入 */
        const val Compact: Int = 150

        /** 标准快速动效 (180ms) - 用于常规状态切换、搜索框一键清空旋转 */
        const val Normal: Int = 180

        /** 中速动效 (250ms) - 用于卡片展开折叠、页面内容局部切换 */
        const val Medium: Int = 250

        /** 标准慢速动效 (350ms) - 用于全屏弹窗、模态底栏滑入滑出 */
        const val Slow: Int = 350

        /** 沉浸式慢动效 (500ms) - 用于大范围结构位移、复杂手势过渡 */
        const val Long: Int = 500
    }

    /**
     * 语义化缓动曲线令牌
     */
    object EasingTokens {
        /** Material 3 标准曲线 (FastOutSlowIn) - 用于大多数进入与位移动画 */
        val Standard: Easing = FastOutSlowInEasing

        /** 减速退出曲线 (LinearOutSlowIn) - 用于淡出或收起动画 */
        val Decelerate: Easing = LinearOutSlowInEasing

        /** 灵动表现力曲线 (Expressive Bezier) - 用于高灵敏度按压回弹与旋转 */
        val Expressive: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

        /** 弹簧阻尼比预设 */
        val SpringMediumBouncy: Float = Spring.DampingRatioMediumBouncy
        val SpringLowStiffness: Float = Spring.StiffnessLow
    }

    /**
     * 常用微交互预设动画规格
     */
    object Specs {
        /** 搜索清空按钮微缩放按压弹簧规格 */
        fun <T> bouncySpring() = spring<T>(
            dampingRatio = EasingTokens.SpringMediumBouncy,
            stiffness = EasingTokens.SpringLowStiffness
        )

        /** 快速淡入规格 */
        val FadeInFast = fadeIn(animationSpec = tween(durationMillis = Duration.Compact))

        /** 快速淡出规格 */
        val FadeOutFast = fadeOut(animationSpec = tween(durationMillis = Duration.Fast))

        /** 伴随微缩放淡入规格 */
        val ScaleFadeIn = fadeIn(animationSpec = tween(durationMillis = Duration.Compact)) +
                scaleIn(initialScale = 0.8f, animationSpec = tween(durationMillis = Duration.Compact))

        /** 伴随微缩放淡出规格 */
        val ScaleFadeOut = fadeOut(animationSpec = tween(durationMillis = Duration.Fast)) +
                scaleOut(targetScale = 0.8f, animationSpec = tween(durationMillis = Duration.Fast))
    }
}
