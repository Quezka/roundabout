package com.andrerinas.openheadunit.roundabout.system

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.andrerinas.openheadunit.R

// view kit for tablet settings + setup ; code-built <- one look, no xml per row
// big + high contrast <- user vision ; touch >= 64dp <- car use
class RbUi(val context: Context) {

    fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
    fun color(id: Int) = ContextCompat.getColor(context, id)

    val regular: Typeface? by lazy { font(R.font.manrope_medium) }
    val bold: Typeface? by lazy { font(R.font.manrope_bold) }
    private fun font(id: Int): Typeface? = try { ResourcesCompat.getFont(context, id) } catch (e: Exception) { null }

    fun rounded(fill: Int, radiusDp: Float = 18f, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        if (stroke != null) setStroke(dp(2f), stroke)
    }

    // ripple @ 5.0+ ; pressed tint below
    fun touchable(fill: Int, radiusDp: Float = 18f): android.graphics.drawable.Drawable {
        val shape = rounded(fill, radiusDp)
        if (Build.VERSION.SDK_INT < 21) return shape
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, rounded(0xFFFFFFFF.toInt(), radiusDp))
    }

    fun text(str: CharSequence, sizeSp: Float = 20f, strong: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = str
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = if (strong) bold else regular
        setTextColor(color(if (muted) R.color.rb_muted else R.color.rb_text))
    }

    fun title(str: CharSequence) = text(str, 30f, strong = true).apply {
        setPadding(0, dp(4f), 0, dp(16f))
    }

    fun header(str: CharSequence) = text(str.toString().uppercase(), 14f, strong = true).apply {
        setTextColor(color(R.color.rb_accent))
        letterSpacing = 0.08f
        setPadding(dp(4f), dp(20f), 0, dp(8f))
    }

    fun note(str: CharSequence) = text(str, 16f, muted = true).apply {
        setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
    }

    fun vertical() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun rowBase(onClick: (() -> Unit)?) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(72f)
        setPadding(dp(20f), dp(10f), dp(16f), dp(10f))
        background = if (onClick != null) touchable(color(R.color.rb_card)) else rounded(color(R.color.rb_card))
        if (onClick != null) setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(8f) }
    }

    private fun labels(titleStr: CharSequence, sub: CharSequence?): Pair<LinearLayout, TextView?> {
        val col = vertical()
        col.addView(text(titleStr, 20f, strong = true))
        val subView = sub?.let { text(it, 16f, muted = true) }
        subView?.let { col.addView(it) }
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        return col to subView
    }

    class Row(val view: LinearLayout, val sub: TextView?)

    fun row(titleStr: CharSequence, sub: CharSequence? = null, trailing: View? = null, onClick: (() -> Unit)? = null): Row {
        val r = rowBase(onClick)
        val (col, subView) = labels(titleStr, sub)
        r.addView(col)
        trailing?.let { r.addView(it) }
        return Row(r, subView)
    }

    // ret = row view ; switch = row.tag
    fun toggle(titleStr: CharSequence, sub: CharSequence?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = SwitchCompat(context).apply {
            isChecked = checked
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(color(R.color.rb_accent), color(R.color.rb_muted)))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(color(R.color.rb_accent_container), color(R.color.rb_container_highest)))
            setOnCheckedChangeListener { _, on -> onChange(on) }
            scaleX = 1.3f; scaleY = 1.3f
        }
        val r = row(titleStr, sub, sw) { sw.toggle() }
        r.view.tag = sw
        return r.view
    }

    fun slider(titleStr: CharSequence, max: Int, value: Int, onChange: (Int) -> Unit): LinearLayout {
        val box = rowBase(null).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.NO_GRAVITY }
        box.addView(text(titleStr, 20f, strong = true))
        box.addView(SeekBar(context).apply {
            this.max = max
            progress = value
            progressDrawable = ContextCompat.getDrawable(context, R.drawable.rb_volume_track)
            thumb = ContextCompat.getDrawable(context, R.drawable.rb_volume_thumb)
            if (Build.VERSION.SDK_INT >= 21) splitTrack = false
            setPadding(dp(14f), 0, dp(14f), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)).apply { topMargin = dp(8f) }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return box
    }

    // segmented pick ; big pills
    fun choice(options: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64f)).apply { bottomMargin = dp(8f) }
        }
        val pills = options.mapIndexed { i, label ->
            text(label, 18f, strong = true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply { if (i > 0) leftMargin = dp(8f) }
            }
        }
        fun paint(sel: Int) = pills.forEachIndexed { i, p ->
            p.background = touchable(color(if (i == sel) R.color.rb_accent else R.color.rb_card))
            p.setTextColor(color(if (i == sel) R.color.rb_on_accent else R.color.rb_text))
        }
        pills.forEachIndexed { i, p -> p.setOnClickListener { paint(i); onPick(i) }; bar.addView(p) }
        paint(selected)
        return bar
    }

    fun button(label: CharSequence, primary: Boolean = true, onClick: () -> Unit) = text(label, 20f, strong = true).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(64f)
        minWidth = dp(180f)
        setPadding(dp(28f), 0, dp(28f), 0)
        background = touchable(color(if (primary) R.color.rb_accent else R.color.rb_container_high), 32f)
        setTextColor(color(if (primary) R.color.rb_on_accent else R.color.rb_text))
        setOnClickListener { onClick() }
    }

    fun spacer(hDp: Float) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(hDp)) }

    fun chevron() = text("›", 34f, muted = true).apply { setPadding(dp(12f), 0, dp(4f), 0) }

    fun value(str: CharSequence) = text(str, 18f, muted = true).apply { setPadding(dp(12f), 0, dp(4f), 0) }

    fun ask(titleStr: CharSequence, message: CharSequence?, ok: CharSequence, onOk: () -> Unit) {
        AlertDialog.Builder(context, R.style.DarkAlertDialog)
            .setTitle(titleStr)
            .apply { if (message != null) setMessage(message) }
            .setPositiveButton(ok) { _, _ -> onOk() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun pick(titleStr: CharSequence, items: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(context, R.style.DarkAlertDialog)
            .setTitle(titleStr)
            .setItems(items.toTypedArray()) { _, i -> onPick(i) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun password(titleStr: CharSequence, onOk: (String) -> Unit) {
        val field = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(color(R.color.rb_text))
            setSingleLine()
        }
        val wrap = FrameLayout(context).apply { setPadding(dp(24f), dp(8f), dp(24f), 0); addView(field) }
        AlertDialog.Builder(context, R.style.DarkAlertDialog)
            .setTitle(titleStr)
            .setView(wrap)
            .setPositiveButton(R.string.rb_connect) { _, _ -> onOk(field.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
        field.requestFocus()
    }
}
