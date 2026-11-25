package com.andrerinas.openheadunit.roundabout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.min

// android 10 style gesture strip @ one screen edge (overlay window)
// side LEFT | RIGHT -> swipe inward = back ; BOTTOM -> swipe up = home ; TOP -> swipe down = quick panel
// feedback: back = arrow bubble @ separate untouchable window (EdgeBubble) ; bottom = handle pill
// ! window = strip only <- a touchable overlay eats every touch inside it, handled or not
@SuppressLint("ViewConstructor")
class EdgeGesture(
    context: Context,
    private val side: Side,
    private val onGesture: (Side) -> Unit,
) : View(context) {

    enum class Side { LEFT, RIGHT, BOTTOM, TOP }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private val trigger = dp(TRIGGER_DP)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var drag = 0f
    private var along = 0f
    private var active = false

    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x8C, 0xFF, 0xFF, 0xFF) }
    var bubble: EdgeBubble? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; drag = 0f; active = true
            }
            MotionEvent.ACTION_MOVE -> if (active) {
                drag = when (side) {
                    Side.LEFT -> e.x - downX
                    Side.RIGHT -> downX - e.x
                    Side.BOTTOM -> downY - e.y
                    Side.TOP -> e.y - downY
                }.coerceAtLeast(0f)
                along = if (side == Side.LEFT || side == Side.RIGHT) e.y else e.x
                bubble?.show(drag / trigger, along)
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (active) {
                active = false
                val fired = drag >= trigger && drag > slop
                drag = 0f
                bubble?.show(0f, along)
                invalidate()
                if (fired) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onGesture(side)
                }
            }
            MotionEvent.ACTION_CANCEL -> { active = false; drag = 0f; bubble?.show(0f, along); invalidate() }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        if (side == Side.BOTTOM) {
            // handle pill: rises with the drag
            val w = dp(96f); val h = dp(4f)
            val lift = min(drag / trigger, 1f) * dp(5f)
            val cy = height - dp(7f) - lift
            c.drawRoundRect(width / 2f - w / 2, cy - h / 2, width / 2f + w / 2, cy + h / 2, h, h, pill)
            return
        }
    }

    companion object {
        const val STRIP_DP = 16f
        const val TRIGGER_DP = 56f
        const val BUBBLE_WINDOW_DP = 96f
    }
}

// back arrow bubble ; untouchable window beside a side strip ; t = drag / trigger
@SuppressLint("ViewConstructor")
class EdgeBubble(context: Context, private val fromLeft: Boolean) : View(context) {
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private var t = 0f
    private var along = 0f
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0xE6, 0x25, 0x28, 0x2D) }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun show(t: Float, along: Float) {
        this.t = t.coerceIn(0f, 1f)
        this.along = along
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (t <= 0f) return
        val r = dp(26f)
        val travel = (r + dp(20f)) * t
        val cx = if (fromLeft) -r + travel else width + r - travel
        val cy = along
        bubble.alpha = (0xE6 * t).toInt()
        c.drawCircle(cx, cy, r, bubble)
        if (t > 0.5f) {
            arrow.alpha = (255 * ((t - 0.5f) * 2f)).toInt()
            val a = dp(8f)
            val dir = if (fromLeft) 1 else -1
            path.reset()
            path.moveTo(cx + dir * a * 0.5f, cy - a)
            path.lineTo(cx - dir * a * 0.5f, cy)
            path.lineTo(cx + dir * a * 0.5f, cy + a)
            c.drawPath(path, arrow)
        }
    }
}
