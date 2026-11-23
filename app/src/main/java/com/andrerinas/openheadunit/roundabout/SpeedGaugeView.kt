package com.andrerinas.openheadunit.roundabout

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.andrerinas.openheadunit.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// roundabout round speed gauge ; port of compose `SpeedGauge` -> plain view <- min sdk 16
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var unit: String = "km/h"
        set(value) { field = value; invalidate() }

    // what the round dial shows ; drive card picker sets it
    enum class Mode { SPEED, ANALOG_CLOCK, DIGITAL_CLOCK }

    var mode: Mode = Mode.SPEED
        set(value) {
            field = value
            removeCallbacks(tickClock)
            if (value != Mode.SPEED && androidx.core.view.ViewCompat.isAttachedToWindow(this)) post(tickClock)
            invalidate()
        }

    private val tickClock = object : Runnable {
        override fun run() {
            invalidate()
            if (mode != Mode.SPEED) postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }
    private val calendar = java.util.Calendar.getInstance()
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = c(R.color.rb_text) }
    private val secondHand = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = c(R.color.rb_accent) }

    private var speedKmh: Int? = null
    private var fraction = 0f
    private var animator: ValueAnimator? = null

    private val disk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c(R.color.rb_container_high) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = c(R.color.rb_accent) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = c(R.color.rb_container_highest)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = c(R.color.rb_accent)
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = c(R.color.rb_outline) }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = font(R.font.manrope_bold)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = c(R.color.rb_muted)
        typeface = font(R.font.manrope_semibold)
    }
    private val oval = RectF()

    init {
        glow.color = (c(R.color.rb_accent) and 0x00FFFFFF) or (0x2E shl 24) // accent @ 18%
    }

    fun setSpeed(kmh: Int?) {
        speedKmh = kmh
        val target = ((kmh ?: 0).toFloat() / GAUGE_MAX_KMH).coerceIn(0f, 1f)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(fraction, target).apply {
            duration = 700
            addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
            start()
        }
        contentDescription = kmh?.let { "$it $unit" }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mode != Mode.SPEED) post(tickClock)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tickClock)
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val d = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (d <= 0f) return
        val r = d / 2f
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f

        canvas.drawCircle(cx, cy, r, disk)
        glow.strokeWidth = r * 0.06f
        canvas.drawCircle(cx, cy, r * 0.95f, glow)
        ring.strokeWidth = r * 0.016f
        canvas.drawCircle(cx, cy, r * 0.95f, ring)

        when (mode) {
            Mode.ANALOG_CLOCK -> { drawAnalog(canvas, cx, cy, r); return }
            Mode.DIGITAL_CLOCK -> { drawDigital(canvas, cx, cy, d); return }
            Mode.SPEED -> {}
        }

        val g = r * 0.78f
        oval.set(cx - g, cy - g, cx + g, cy + g)
        track.strokeWidth = r * 0.085f
        arc.strokeWidth = r * 0.085f
        canvas.drawArc(oval, 135f, 270f, false, track)
        if (fraction > 0.002f) canvas.drawArc(oval, 135f, 270f * fraction, false, arc)

        tick.strokeWidth = r * 0.02f
        for (i in 0..8) {
            val a = Math.toRadians(135.0 + 270.0 * i / 8)
            val r1 = r * 0.64f
            val r2 = r * 0.6f
            canvas.drawLine(
                cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(),
                cx + (cos(a) * r2).toFloat(), cy + (sin(a) * r2).toFloat(), tick
            )
        }

        big.textSize = d * 0.26f
        big.color = c(if (speedKmh == null) R.color.rb_outline else R.color.rb_text)
        small.textSize = d * 0.075f
        val gap = d * 0.02f
        val bigH = -big.ascent() - big.descent() // digits ~ cap height
        val smallH = -small.ascent()
        val top = cy - (bigH + gap + smallH) / 2f
        canvas.drawText(speedKmh?.toString() ?: "--", cx, top + bigH, big)
        canvas.drawText(unit, cx, top + bigH + gap + smallH, small)
    }

    private fun drawAnalog(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        calendar.timeInMillis = System.currentTimeMillis()
        tick.strokeWidth = r * 0.025f
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90.0)
            val hour = i % 5 == 0
            val r1 = r * 0.82f
            val r2 = if (hour) r * 0.70f else r * 0.78f
            tick.color = c(if (hour) R.color.rb_muted else R.color.rb_container_highest)
            tick.strokeWidth = if (hour) r * 0.03f else r * 0.015f
            canvas.drawLine(
                cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(),
                cx + (cos(a) * r2).toFloat(), cy + (sin(a) * r2).toFloat(), tick
            )
        }
        tick.color = c(R.color.rb_outline)
        val sec = calendar.get(java.util.Calendar.SECOND)
        val min = calendar.get(java.util.Calendar.MINUTE) + sec / 60f
        val hr = calendar.get(java.util.Calendar.HOUR) + min / 60f
        fun handLine(turns: Float, len: Float, width: Float, p: Paint) {
            val a = Math.toRadians(turns * 360.0 - 90.0)
            p.strokeWidth = width
            canvas.drawLine(cx, cy, cx + (cos(a) * len).toFloat(), cy + (sin(a) * len).toFloat(), p)
        }
        handLine(hr / 12f, r * 0.42f, r * 0.07f, hand)
        handLine(min / 60f, r * 0.62f, r * 0.045f, hand)
        handLine(sec / 60f, r * 0.68f, r * 0.018f, secondHand)
        canvas.drawCircle(cx, cy, r * 0.05f, secondHand)
    }

    private fun drawDigital(canvas: Canvas, cx: Float, cy: Float, d: Float) {
        val now = java.util.Date()
        val time = android.text.format.DateFormat.getTimeFormat(context).format(now)
        val day = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(now)
        big.textSize = d * (if (time.length > 5) 0.17f else 0.22f)
        big.color = c(R.color.rb_text)
        small.textSize = d * 0.075f
        val gap = d * 0.03f
        val bigH = -big.ascent() - big.descent()
        val smallH = -small.ascent()
        val top = cy - (bigH + gap + smallH) / 2f
        canvas.drawText(time, cx, top + bigH, big)
        canvas.drawText(day, cx, top + bigH + gap + smallH, small)
    }

    private fun c(id: Int) = ContextCompat.getColor(context, id)

    private fun font(id: Int): Typeface? =
        try { ResourcesCompat.getFont(context, id) } catch (e: Exception) { Typeface.DEFAULT_BOLD }

    companion object {
        const val GAUGE_MAX_KMH = 160
    }
}
