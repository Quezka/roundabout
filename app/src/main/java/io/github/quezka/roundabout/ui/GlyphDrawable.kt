package io.github.quezka.roundabout.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

// view-side glyphs for rail overlay ; mirror of Glyphs.kt shapes
class GlyphDrawable(private val glyph: Glyph, color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setColor(c: Int) {
        paint.color = c
        stroke.color = c
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        stroke.strokeWidth = w * 0.13f
        when (glyph) {
            Glyph.BACK -> canvas.drawPath(Path().apply {
                moveTo(w * 0.62f, h * 0.16f); lineTo(w * 0.28f, h * 0.5f); lineTo(w * 0.62f, h * 0.84f)
            }, stroke)
            Glyph.HOME -> {
                canvas.drawPath(Path().apply {
                    moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.9f, h * 0.46f); lineTo(w * 0.78f, h * 0.46f)
                    lineTo(w * 0.78f, h * 0.88f); lineTo(w * 0.22f, h * 0.88f); lineTo(w * 0.22f, h * 0.46f)
                    lineTo(w * 0.1f, h * 0.46f); close()
                }, paint)
            }
            Glyph.GRID -> {
                val cell = w * 0.24f
                val gap = w * 0.1f
                val start = (w - cell * 3 - gap * 2) / 2
                for (i in 0..2) for (j in 0..2) {
                    val x = start + i * (cell + gap)
                    val y = start + j * (cell + gap)
                    canvas.drawRoundRect(RectF(x, y, x + cell, y + cell), cell * 0.3f, cell * 0.3f, paint)
                }
            }
            Glyph.BOLT -> canvas.drawPath(Path().apply {
                moveTo(w * 0.58f, h * 0.04f); lineTo(w * 0.2f, h * 0.56f); lineTo(w * 0.48f, h * 0.56f)
                lineTo(w * 0.4f, h * 0.96f); lineTo(w * 0.8f, h * 0.42f); lineTo(w * 0.52f, h * 0.42f); close()
            }, paint)
            else -> canvas.drawCircle(w / 2, h / 2, w * 0.3f, paint)
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; stroke.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; stroke.colorFilter = colorFilter }
    @Deprecated("drawable api")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
