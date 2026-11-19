package io.github.quezka.roundabout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// own icons drawn @ canvas <- no icon dep
enum class Glyph { PREVIOUS, PLAY, PAUSE, NEXT, PIN, NOTE, GRID, LIGHT, GEAR, BACK, BOLT, HOME }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) { draw(glyph, color) }
}

private fun DrawScope.draw(g: Glyph, c: Color) {
    val w = size.width
    val h = size.height
    val s = w * 0.1f
    when (g) {
        Glyph.PLAY -> drawPath(Path().apply {
            moveTo(w * 0.28f, h * 0.18f); lineTo(w * 0.84f, h * 0.5f); lineTo(w * 0.28f, h * 0.82f); close()
        }, c)
        Glyph.PAUSE -> {
            val r = CornerRadius(s * 0.6f)
            drawRoundRect(c, Offset(w * 0.24f, h * 0.18f), Size(w * 0.18f, h * 0.64f), r)
            drawRoundRect(c, Offset(w * 0.58f, h * 0.18f), Size(w * 0.18f, h * 0.64f), r)
        }
        Glyph.NEXT, Glyph.PREVIOUS -> {
            val flip = g == Glyph.PREVIOUS
            fun x(v: Float) = if (flip) w - v else v
            drawPath(Path().apply {
                moveTo(x(w * 0.16f), h * 0.2f); lineTo(x(w * 0.66f), h * 0.5f); lineTo(x(w * 0.16f), h * 0.8f); close()
            }, c)
            drawRoundRect(c, Offset(if (flip) w * 0.16f else w * 0.7f, h * 0.2f), Size(w * 0.14f, h * 0.6f), CornerRadius(s * 0.5f))
        }
        Glyph.PIN -> {
            drawPath(Path().apply {
                moveTo(w * 0.5f, h * 0.94f)
                lineTo(w * 0.2f, h * 0.48f)
                arcTo(androidx.compose.ui.geometry.Rect(w * 0.18f, h * 0.06f, w * 0.82f, h * 0.7f), 150f, 240f, false)
                close()
            }, c)
            drawCircle(Color.Black, radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.38f), blendMode = BlendMode.Clear)
        }
        Glyph.NOTE -> {
            drawCircle(c, radius = w * 0.16f, center = Offset(w * 0.34f, h * 0.76f))
            drawRect(c, Offset(w * 0.44f, h * 0.12f), Size(w * 0.1f, h * 0.66f))
            drawPath(Path().apply {
                moveTo(w * 0.44f, h * 0.12f); lineTo(w * 0.82f, h * 0.24f); lineTo(w * 0.82f, h * 0.42f); lineTo(w * 0.54f, h * 0.32f); close()
            }, c)
        }
        Glyph.GRID -> {
            val cell = w * 0.24f
            val gap = w * 0.1f
            val start = (w - cell * 3 - gap * 2) / 2
            for (i in 0..2) for (j in 0..2) drawRoundRect(
                c, Offset(start + i * (cell + gap), start + j * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.3f),
            )
        }
        Glyph.LIGHT -> {
            drawCircle(c, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.5f))
            for (i in 0 until 8) rotate(i * 45f) {
                drawLine(c, Offset(w * 0.5f, h * 0.06f), Offset(w * 0.5f, h * 0.2f), strokeWidth = s, cap = StrokeCap.Round)
            }
        }
        Glyph.GEAR -> {
            for (i in 0 until 8) rotate(i * 45f) {
                drawRoundRect(c, Offset(w * 0.42f, h * 0.06f), Size(w * 0.16f, h * 0.24f), CornerRadius(s * 0.4f))
            }
            drawCircle(c, radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.5f))
            drawCircle(Color.Black, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f), blendMode = BlendMode.Clear)
        }
        Glyph.BACK -> drawPath(Path().apply {
            moveTo(w * 0.62f, h * 0.16f); lineTo(w * 0.28f, h * 0.5f); lineTo(w * 0.62f, h * 0.84f)
        }, c, style = Stroke(width = s * 1.4f, cap = StrokeCap.Round))
        Glyph.HOME -> drawPath(Path().apply {
            moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.9f, h * 0.46f); lineTo(w * 0.78f, h * 0.46f)
            lineTo(w * 0.78f, h * 0.88f); lineTo(w * 0.22f, h * 0.88f); lineTo(w * 0.22f, h * 0.46f)
            lineTo(w * 0.1f, h * 0.46f); close()
        }, c)
        Glyph.BOLT -> drawPath(Path().apply {
            moveTo(w * 0.58f, h * 0.04f); lineTo(w * 0.2f, h * 0.56f); lineTo(w * 0.48f, h * 0.56f)
            lineTo(w * 0.4f, h * 0.96f); lineTo(w * 0.8f, h * 0.42f); lineTo(w * 0.52f, h * 0.42f); close()
        }, c)
    }
}
