@file:Suppress("ktlint:standard:property-naming")

package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object PremiumIcons {
    private fun featherBuilder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private val strokeColor = SolidColor(Color.Black)
    private const val strokeWidth = 2f

    val History: ImageVector by lazy {
        featherBuilder("FeatherClock")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 21f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0f, dy1 = -18f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0f, dy1 = 18f)
                close()
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 6f)
                verticalLineTo(12f)
                lineTo(16f, 14f)
            }
            .build()
    }

    val Home: ImageVector by lazy {
        featherBuilder("FeatherHome")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 9f)
                lineTo(12f, 2f)
                lineTo(21f, 9f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
                close()
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 22f)
                verticalLineTo(12f)
                horizontalLineTo(15f)
                verticalLineTo(22f)
            }
            .build()
    }

    val Search: ImageVector by lazy {
        featherBuilder("FeatherSearch")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(11f, 19f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0f, dy1 = -16f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0f, dy1 = 16f)
                close()
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
            }
            .build()
    }

    val Library: ImageVector by lazy {
        featherBuilder("FeatherBookmark")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19f, 21f)
                lineTo(12f, 16f)
                lineTo(5f, 21f)
                verticalLineTo(5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
                horizontalLineTo(17f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
                close()
            }
            .build()
    }

    val Extensions: ImageVector by lazy {
        featherBuilder("FeatherPackage")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Top isometric diamond face
                moveTo(12f, 2f)
                lineTo(21f, 7f)
                lineTo(12f, 12f)
                lineTo(3f, 7f)
                close()
                // Left body
                moveTo(3f, 7f)
                lineTo(3f, 17f)
                lineTo(12f, 22f)
                lineTo(12f, 12f)
                // Right body
                moveTo(21f, 7f)
                lineTo(21f, 17f)
                lineTo(12f, 22f)
                // Top flap crease
                moveTo(7.5f, 4.5f)
                lineTo(16.5f, 9.5f)
            }
            .build()
    }

    val Settings: ImageVector by lazy {
        featherBuilder("FeatherSettings")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15f, 12f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -6f, dy1 = 0f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 6f, dy1 = 0f)
                close()
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.4f, 15f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0.33f, dy1 = 1.82f)
                lineToRelative(0.06f, 0.06f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0f, dy1 = 2.83f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2.83f, dy1 = 0f)
                lineToRelative(-0.06f, -0.06f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -1.82f, dy1 = -0.33f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -1f, dy1 = 1.51f)
                verticalLineTo(21f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
                verticalLineToRelative(-0.09f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -1f, dy1 = -1.51f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -1.82f, dy1 = 0.33f)
                lineToRelative(-0.06f, 0.06f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2.83f, dy1 = 0f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0f, dy1 = -2.83f)
                lineToRelative(0.06f, -0.06f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0.33f, dy1 = -1.82f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -1.51f, dy1 = -1f)
                horizontalLineTo(3f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
                horizontalLineToRelative(0.09f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.51f, dy1 = -1f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -0.33f, dy1 = -1.82f)
                lineToRelative(-0.06f, -0.06f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0f, dy1 = -2.83f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2.83f, dy1 = 0f)
                lineToRelative(0.06f, 0.06f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.82f, dy1 = 0.33f)
                horizontalLineTo(9f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1f, dy1 = -1.51f)
                verticalLineTo(3f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
                verticalLineToRelative(0.09f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1f, dy1 = 1.51f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.82f, dy1 = -0.33f)
                lineToRelative(0.06f, -0.06f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2.83f, dy1 = 0f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0f, dy1 = 2.83f)
                lineToRelative(-0.06f, 0.06f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -0.33f, dy1 = 1.82f)
                verticalLineTo(9f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 1.51f, dy1 = 1f)
                horizontalLineTo(21f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
                horizontalLineToRelative(-0.09f)
                arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -1.51f, dy1 = 1f)
                close()
            }
            .build()
    }

    val Updates: ImageVector by lazy {
        featherBuilder("FeatherBell")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(18f, 8f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -12f, dy1 = 0f)
                curveToRelative(0f, 7f, -3f, 9f, -3f, 9f)
                horizontalLineTo(21f)
                curveToRelative(0f, 0f, -3f, -2f, -3f, -9f)
                close()
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(13.73f, 21f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -3.46f, dy1 = 0f)
            }
            .build()
    }

    val Explore: ImageVector by lazy {
        featherBuilder("FeatherCompass")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 22f)
                arcToRelative(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0f, dy1 = -20f)
                arcToRelative(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 0f, dy1 = 20f)
                close()
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(16.24f, 7.76f)
                lineToRelative(-2.12f, 6.36f)
                lineToRelative(-6.36f, 2.12f)
                lineToRelative(2.12f, -6.36f)
                lineToRelative(6.36f, -2.12f)
                close()
            }
            .build()
    }

    val Downloads: ImageVector by lazy {
        featherBuilder("FeatherDownload")
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(21f, 15f)
                verticalLineToRelative(4f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
                verticalLineToRelative(-4f)
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 10f)
                lineToRelative(5f, 5f)
                lineToRelative(5f, -5f)
            }
            .path(
                stroke = strokeColor,
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 15f)
                verticalLineTo(3f)
            }
            .build()
    }
}
