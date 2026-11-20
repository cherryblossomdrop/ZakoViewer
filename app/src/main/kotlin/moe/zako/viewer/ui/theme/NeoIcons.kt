package moe.zako.viewer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NeoIcons {
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
}
