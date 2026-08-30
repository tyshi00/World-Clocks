package com.tyshi00.worldclocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * "compare_arrows" from Google's Material Symbols (Outlined) - used for the
 * Compare button since LightIcons has no dedicated compare/exchange glyph.
 *
 * Built as an ImageVector rather than a static drawable resource so it can
 * be tinted to match the current theme at render time. LightBarButton.Icon
 * renders custom icons through a plain Image() with no ColorFilter applied
 * (unlike LightBarButton.LightIcon, which tints internally), so a static
 * asset would stay whatever color it was drawn with regardless of dark/light
 * theme or Invert Colors - this recomputes the fill color itself instead.
 */
@Composable
fun rememberCompareArrowsPainter(tint: Color): Painter {
    val vector = remember(tint) {
        ImageVector.Builder(
            name = "compare_arrows",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(tint),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(8f, 20f)
                lineTo(6.6f, 18.58f)
                lineTo(9.18f, 16f)
                horizontalLineTo(2f)
                verticalLineTo(14f)
                horizontalLineTo(9.18f)
                lineTo(6.6f, 11.43f)
                lineTo(8f, 10f)
                lineToRelative(5f, 5f)
                lineTo(8f, 20f)
                close()
                moveToRelative(8f, -6f)
                lineTo(11f, 9f)
                lineTo(16f, 4f)
                lineToRelative(1.4f, 1.43f)
                lineTo(14.83f, 8f)
                horizontalLineTo(22f)
                verticalLineToRelative(2f)
                horizontalLineTo(14.83f)
                lineToRelative(2.58f, 2.57f)
                lineTo(16f, 14f)
                close()
            }
        }.build()
    }
    return rememberVectorPainter(vector)
}
