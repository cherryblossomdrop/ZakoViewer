package moe.zako.viewer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ZakoIcons {
    val WatchLaterOutlined: ImageVector by lazy {
        ImageVector.Builder(
            name = "WatchLaterOutlined",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 顺时针环形箭头圆弧
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(17.5f, 18.5f)
                arcTo(
                    horizontalEllipseRadius = 8.5f,
                    verticalEllipseRadius = 8.5f,
                    theta = 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = true,
                    x1 = 20.5f,
                    y1 = 11.3f
                )
            }
            // 右侧向下箭头
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(18.2f, 11.3f)
                lineTo(22.8f, 11.3f)
                lineTo(20.5f, 15.2f)
                close()
            }
            // 中心空心播放三角形
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.2f, 9.2f)
                lineTo(15.2f, 12f)
                lineTo(10.2f, 14.8f)
                close()
            }
        }.build()
    }

    val WatchLaterFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "WatchLaterFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 顺时针环形箭头圆弧
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(17.5f, 18.5f)
                arcTo(
                    horizontalEllipseRadius = 8.5f,
                    verticalEllipseRadius = 8.5f,
                    theta = 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = true,
                    x1 = 20.5f,
                    y1 = 11.3f
                )
            }
            // 右侧向下箭头
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(18.2f, 11.3f)
                lineTo(22.8f, 11.3f)
                lineTo(20.5f, 15.2f)
                close()
            }
            // 中心实心播放三角形
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(10f, 9f)
                lineTo(15.2f, 12f)
                lineTo(10f, 15f)
                close()
            }
        }.build()
    }

    // 默认通用别名
    val WatchLater: ImageVector get() = WatchLaterOutlined

    /**
     * 自定义“进入全屏”矢量图标：四角向外扩展，紧凑占满 24.dp 标准视觉区域
     */
    val Fullscreen: ImageVector by lazy {
        ImageVector.Builder(
            name = "Fullscreen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 左上外扩
                moveTo(3f, 9f)
                lineTo(3f, 3f)
                lineTo(9f, 3f)
                moveTo(3.5f, 3.5f)
                lineTo(9.5f, 9.5f)

                // 右上外扩
                moveTo(15f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 9f)
                moveTo(20.5f, 3.5f)
                lineTo(14.5f, 9.5f)

                // 右下外扩
                moveTo(21f, 15f)
                lineTo(21f, 21f)
                lineTo(15f, 21f)
                moveTo(20.5f, 20.5f)
                lineTo(14.5f, 14.5f)

                // 左下外扩
                moveTo(9f, 21f)
                lineTo(3f, 21f)
                lineTo(3f, 15f)
                moveTo(3.5f, 20.5f)
                lineTo(9.5f, 14.5f)
            }
        }.build()
    }

    /**
     * 自定义“退出全屏”矢量图标：四角向内收缩，与进入全屏图标体量、粗细完全一致
     */
    val FullscreenExit: ImageVector by lazy {
        ImageVector.Builder(
            name = "FullscreenExit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 左上内收
                moveTo(3.5f, 9.5f)
                lineTo(9.5f, 9.5f)
                lineTo(9.5f, 3.5f)
                moveTo(9.5f, 9.5f)
                lineTo(3.5f, 3.5f)

                // 右上内收
                moveTo(14.5f, 3.5f)
                lineTo(14.5f, 9.5f)
                lineTo(20.5f, 9.5f)
                moveTo(14.5f, 9.5f)
                lineTo(20.5f, 3.5f)

                // 右下内收
                moveTo(20.5f, 14.5f)
                lineTo(14.5f, 14.5f)
                lineTo(14.5f, 20.5f)
                moveTo(14.5f, 14.5f)
                lineTo(20.5f, 20.5f)

                // 左下内收
                moveTo(9.5f, 20.5f)
                lineTo(9.5f, 14.5f)
                lineTo(3.5f, 14.5f)
                moveTo(9.5f, 14.5f)
                lineTo(3.5f, 20.5f)
            }
        }.build()
    }

    /**
     * 自定义“画面比例”矢量图标：经典播放器画幅调节视窗，外层为标准圆角屏幕，内部为双向比例拉伸缩放标尺 (< | >)
     */
    val AspectRatio: ImageVector by lazy {
        ImageVector.Builder(
            name = "AspectRatio",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 外层：圆角宽屏画框 (18 x 14，视觉饱满匀称)
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.5f, 5f)
                lineTo(18.5f, 5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 21f, 7.5f)
                lineTo(21f, 16.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 18.5f, 19f)
                lineTo(5.5f, 19f)
                arcTo(2.5f, 2.5f, 0f, false, true, 3f, 16.5f)
                lineTo(3f, 7.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 5.5f, 5f)
                close()
            }
            // 内部：双向比例缩放箭头与居中分界标尺 (< | >)
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 左箭头
                moveTo(9.5f, 9.5f)
                lineTo(7f, 12f)
                lineTo(9.5f, 14.5f)

                // 右箭头
                moveTo(14.5f, 9.5f)
                lineTo(17f, 12f)
                lineTo(14.5f, 14.5f)

                // 横向标尺线
                moveTo(7.5f, 12f)
                lineTo(16.5f, 12f)

                // 居中基准刻度
                moveTo(12f, 9.5f)
                lineTo(12f, 14.5f)
            }
        }.build()
    }
}

typealias NeoIcons = ZakoIcons
