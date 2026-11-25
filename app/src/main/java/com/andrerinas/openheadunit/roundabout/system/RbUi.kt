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
import com.andrerinas.openheadunit.main.settings.GroupPosition
import com.andrerinas.openheadunit.main.settings.SettingsDrawables
import com.andrerinas.openheadunit.main.settings.SettingsPalette

// view kit for tablet settings + setup ; code-built
// look = open headunit settings rows (palette, manrope semibold, joined rounded groups, m3 switch)
// => tablet tab @ roundabout settings = same look as the rest
class RbUi(val context: Context) {

    fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()
    fun color(id: Int) = ContextCompat.getColor(context, id)
    private fun dimen(id: Int) = context.resources.getDimensionPixelSize(id)
    private fun sp(id: Int) = context.resources.getDimension(id)

    val palette: SettingsPalette by lazy { SettingsPalette.from(context) }
    val regular: Typeface? by lazy { font(R.font.manrope_medium) }
    val bold: Typeface? by lazy { font(R.font.manrope_bold) }
    val semibold: Typeface? by lazy { font(R.font.manrope_semibold) }
    private fun font(id: Int): Typeface? = try { ResourcesCompat.getFont(context, id) } catch (e: Exception) { null }

    fun rounded(fill: Int, radiusDp: Float = 16f, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        if (stroke != null) setStroke(dp(2f), stroke)
    }

    // ripple @ 5.0+ ; pressed tint below
    fun touchable(fill: Int, radiusDp: Float = 16f): android.graphics.drawable.Drawable {
        val shape = rounded(fill, radiusDp)
        if (Build.VERSION.SDK_INT < 21) return shape
        return RippleDrawable(ColorStateList.valueOf(0x1FFFFFFF), shape, rounded(0xFFFFFFFF.toInt(), radiusDp))
    }

    fun text(str: CharSequence, sizeSp: Float = 15f, strong: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = str
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = if (strong) semibold else regular
        setTextColor(if (muted) palette.onSurfaceVariant else palette.onSurface)
    }

    // page title = settings section header (primary, small)
    fun title(str: CharSequence) = TextView(context).apply {
        text = str
        typeface = semibold
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(R.dimen.settings_section_title_size))
        setTextColor(palette.primary)
        val pad = dimen(R.dimen.settings_row_padding_start)
        setPadding(pad, dimen(R.dimen.settings_section_title_padding_top) + dimen(R.dimen.settings_section_spacing), pad,
            dimen(R.dimen.settings_section_title_padding_bottom))
    }

    // group label = settings subheader
    fun header(str: CharSequence) = TextView(context).apply {
        text = str.toString().uppercase()
        typeface = semibold
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(R.dimen.settings_row_description_size))
        setTextColor(palette.onSurfaceVariant)
        val pad = dimen(R.dimen.settings_row_padding_start)
        val v = dimen(R.dimen.settings_section_title_padding_bottom)
        setPadding(pad, v + dimen(R.dimen.settings_section_spacing), pad, v)
    }

    fun note(str: CharSequence) = TextView(context).apply {
        text = str
        typeface = regular
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(R.dimen.settings_row_description_size))
        setTextColor(palette.onSurfaceVariant)
        val pad = dimen(R.dimen.settings_row_padding_start)
        setPadding(pad, dp(6f), pad, dp(6f))
    }

    fun vertical() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    // rows join neighbours into one rounded card ; shape set by regroup
    private val groupRows = java.util.WeakHashMap<View, Boolean>()
    private val accented = java.util.WeakHashMap<View, Boolean>()

    fun accent(row: View) { accented[row] = true; (row.parent as? ViewGroup)?.let { regroup(it) } }

    private fun rowBase(onClick: (() -> Unit)?) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dimen(R.dimen.settings_row_min_height)
        val v = dimen(R.dimen.settings_row_padding_vertical)
        setPadding(dimen(R.dimen.settings_row_padding_start), v, dp(16f), v)
        background = SettingsDrawables.groupItem(context, palette, GroupPosition.SINGLE, sp(R.dimen.settings_group_radius))
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(2f) }
        groupRows[this] = true
    }

    /** Joined-group corners for every row run in [container] (and nested lists). */
    fun regroup(container: ViewGroup) {
        val radius = sp(R.dimen.settings_group_radius)
        val kids = (0 until container.childCount).map { container.getChildAt(it) }
        kids.forEachIndexed { i, v ->
            if (v.visibility == View.GONE) return@forEachIndexed
            if (groupRows[v] != true) {
                if (v is LinearLayout && v.orientation == LinearLayout.VERTICAL) regroup(v)
                return@forEachIndexed
            }
            fun isRow(j: Int) = kids.getOrNull(j)?.let { groupRows[it] == true && it.visibility != View.GONE } == true
            val pos = GroupPosition.of(isFirst = !isRow(i - 1), isLast = !isRow(i + 1))
            val fill = if (accented[v] == true) palette.secondaryContainer else palette.container
            v.background = SettingsDrawables.groupItem(context, palette, pos, radius, fill)
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val gap = if (pos == GroupPosition.BOTTOM || pos == GroupPosition.SINGLE) dimen(R.dimen.settings_section_spacing) else dp(2f)
                if (lp.bottomMargin != gap) { lp.bottomMargin = gap; v.layoutParams = lp }
            }
        }
    }

    /** Keeps [container] grouped as panels add and drop rows. */
    fun autoGroup(container: ViewGroup) {
        var queued = false
        val run = Runnable { queued = false; regroup(container) }
        container.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                if (child is LinearLayout && groupRows[child] != true) autoGroup(child)
                if (!queued) { queued = true; container.post(run) }
            }
            override fun onChildViewRemoved(parent: View?, child: View?) {
                if (!queued) { queued = true; container.post(run) }
            }
        })
    }

    private fun labels(titleStr: CharSequence, sub: CharSequence?): Pair<LinearLayout, TextView?> {
        val col = vertical()
        col.addView(TextView(context).apply {
            text = titleStr
            typeface = semibold
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(R.dimen.settings_row_title_size))
            setTextColor(palette.onSurface)
        })
        val subView = sub?.let { str ->
            TextView(context).apply {
                text = str
                typeface = regular
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(R.dimen.settings_row_description_size))
                setTextColor(palette.onSurfaceVariant)
                setPadding(0, dp(2f), 0, 0)
            }
        }
        subView?.let { col.addView(it) }
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        return col to subView
    }

    class Row(val view: LinearLayout, val sub: TextView?)

    // clickable + no trailing => chevron (like settings sub-screens)
    fun row(titleStr: CharSequence, sub: CharSequence? = null, trailing: View? = null, onClick: (() -> Unit)? = null): Row {
        val r = rowBase(onClick)
        val (col, subView) = labels(titleStr, sub)
        r.addView(col)
        (trailing ?: if (onClick != null) chevron() else null)?.let { r.addView(it) }
        return Row(r, subView)
    }

    // ret = row view ; switch = row.tag
    fun toggle(titleStr: CharSequence, sub: CharSequence?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = SwitchCompat(context).apply {
            isChecked = checked
            trackDrawable = SettingsDrawables.switchTrack(context, palette)
            thumbDrawable = SettingsDrawables.switchThumb(context, palette)
            background = null
            setOnCheckedChangeListener { _, on -> onChange(on) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { leftMargin = dp(16f) }
        }
        val r = row(titleStr, sub, sw) { sw.toggle() }
        r.view.tag = sw
        return r.view
    }

    fun slider(titleStr: CharSequence, max: Int, value: Int, onChange: (Int) -> Unit): LinearLayout {
        val box = rowBase(null).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.NO_GRAVITY }
        box.addView(labels(titleStr, null).first.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
        box.addView(SeekBar(context).apply {
            this.max = max
            progress = value
            if (Build.VERSION.SDK_INT >= 21) {
                progressTintList = ColorStateList.valueOf(palette.primary)
                thumbTintList = ColorStateList.valueOf(palette.primary)
                progressBackgroundTintList = ColorStateList.valueOf(palette.containerHighest)
            }
            minimumHeight = dp(40f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) }
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply { bottomMargin = dp(8f) }
        }
        val pills = options.mapIndexed { i, label ->
            text(label, 15f, strong = true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply { if (i > 0) leftMargin = dp(8f) }
            }
        }
        fun paint(sel: Int) = pills.forEachIndexed { i, p ->
            p.background = touchable(if (i == sel) palette.primary else palette.container, 24f)
            p.setTextColor(if (i == sel) palette.onPrimary else palette.onSurface)
        }
        pills.forEachIndexed { i, p -> p.setOnClickListener { paint(i); onPick(i) }; bar.addView(p) }
        paint(selected)
        return bar
    }

    fun button(label: CharSequence, primary: Boolean = true, onClick: () -> Unit) = text(label, 16f, strong = true).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(52f)
        minWidth = dp(160f)
        setPadding(dp(24f), 0, dp(24f), 0)
        background = touchable(if (primary) palette.primary else palette.containerHigh, 26f)
        setTextColor(if (primary) palette.onPrimary else palette.onSurface)
        setOnClickListener { onClick() }
    }

    fun spacer(hDp: Float) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(hDp)) }

    fun chevron() = android.widget.ImageView(context).apply {
        setImageDrawable(SettingsDrawables.tinted(context, R.drawable.ic_sym_chevron_right, palette.onSurfaceVariant))
        val size = dimen(R.dimen.settings_row_icon_size)
        layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(8f) }
    }

    fun value(str: CharSequence) = TextView(context).apply {
        text = str
        typeface = regular
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(R.dimen.settings_row_value_size))
        setTextColor(palette.onSurfaceVariant)
        setPadding(dp(16f), 0, dp(4f), 0)
    }

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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(palette.onSurface)
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
