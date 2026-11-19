package io.github.quezka.roundabout.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.min
import io.github.quezka.roundabout.domain.Speed
import kotlin.math.cos
import kotlin.math.sin

// round speed gauge ; led ring = primary
@Composable
fun SpeedGauge(speedKmh: Int?, unit: String, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(Speed.gaugeFraction(speedKmh), tween(700), label = "gauge")
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d: Dp = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        fun sp(f: Float): TextUnit = with(density) { (d * f).toSp() }
        Box(Modifier.size(d).testTag("gauge"), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                val c = center
                drawCircle(cs.surfaceContainerHigh, r)
                drawCircle(cs.primary.copy(alpha = 0.18f), r * 0.95f, style = Stroke(r * 0.06f))
                drawCircle(cs.primary, r * 0.95f, style = Stroke(r * 0.016f))
                val g = r * 0.78f
                val tl = Offset(c.x - g, c.y - g)
                val sz = Size(g * 2, g * 2)
                drawArc(cs.surfaceContainerHighest, 135f, 270f, false, tl, sz, style = Stroke(r * 0.085f, cap = StrokeCap.Round))
                if (fraction > 0.002f) {
                    drawArc(cs.primary, 135f, 270f * fraction, false, tl, sz, style = Stroke(r * 0.085f, cap = StrokeCap.Round))
                }
                for (i in 0..8) {
                    val a = Math.toRadians(135.0 + 270.0 * i / 8)
                    val r1 = r * 0.64f
                    val r2 = r * 0.6f
                    drawLine(
                        cs.outline,
                        Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()),
                        Offset(c.x + (cos(a) * r2).toFloat(), c.y + (sin(a) * r2).toFloat()),
                        strokeWidth = r * 0.02f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    speedKmh?.toString() ?: "--",
                    color = if (speedKmh == null) cs.outline else cs.onSurface,
                    fontSize = sp(0.26f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("speed"),
                )
                Text(unit, color = cs.onSurfaceVariant, fontSize = sp(0.075f), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
