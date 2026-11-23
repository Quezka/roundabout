package com.andrerinas.openheadunit.roundabout

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.Settings

// roundabout rail beside android auto: clock, battery, home (dashboard), aa menu
// lives in the left inset (settings.insetLeft) => projection never under it
// no rail if inset < MIN_DP <- user turned it off in custom insets
class RoundaboutAaRail private constructor(private val activity: Activity, private val root: View) {
    private var battery: TextView? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = renderBattery(i)
    }

    fun detach() {
        try { activity.unregisterReceiver(receiver) } catch (e: Exception) { }
        (root.parent as? ViewGroup)?.removeView(root)
    }

    private fun renderBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        battery?.text = (if (charging) "⚡" else "") + "${level * 100 / scale}%"
    }

    companion object {
        const val RAIL_DP = 112f
        private const val MIN_DP = 80f
        private const val MIGRATED_KEY = "rb-aa-rail-inset-v1"

        private fun dp(c: Context, v: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

        // once per install: left inset = rail width ; user may change it later in custom insets
        fun ensureInset(context: Context, settings: Settings) {
            val prefs = context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(MIGRATED_KEY, false)) return
            if (settings.insetLeft == 0) settings.insetLeft = dp(context, RAIL_DP)
            prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
        }

        fun attach(activity: Activity, settings: Settings, onHome: () -> Unit, onMenu: () -> Unit): RoundaboutAaRail? {
            val width = settings.insetLeft
            if (width < dp(activity, MIN_DP)) return null
            val font: Typeface? = try { ResourcesCompat.getFont(activity, R.font.manrope_bold) } catch (e: Exception) { null }
            val c = activity
            val rootView = FrameLayout(c).apply { setBackgroundColor(ContextCompat.getColor(c, R.color.splash_background)) }
            val panel = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = ContextCompat.getDrawable(c, R.drawable.rb_rail_bg)
                setPadding(0, dp(c, 14f), 0, dp(c, 12f))
            }
            rootView.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                val m = dp(c, 8f)
                setMargins(m, m, m, m)
            })
            panel.addView(TextClock(c).apply {
                setTextColor(ContextCompat.getColor(c, R.color.rb_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = font ?: Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
            })
            val batteryView = TextView(c).apply {
                setTextColor(ContextCompat.getColor(c, R.color.rb_muted))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = font ?: Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            panel.addView(batteryView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(c, 2f) })
            panel.addView(View(c), LinearLayout.LayoutParams(1, 0, 1f))
            panel.addView(button(c, R.drawable.rb_ic_aa_menu, R.drawable.rb_rail_btn, R.color.rb_text,
                c.getString(R.string.exit_dialog_title), onMenu))
            panel.addView(button(c, R.drawable.rb_ic_dashboard, R.drawable.rb_rail_btn_accent, R.color.rb_on_accent,
                c.getString(R.string.rb_exit_home), onHome).also {
                (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(c, 10f)
            })
            activity.addContentView(rootView, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.TOP))
            return RoundaboutAaRail(activity, rootView).also { rail ->
                rail.battery = batteryView
                c.registerReceiver(rail.receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { rail.renderBattery(it) }
            }
        }

        private fun button(c: Context, icon: Int, bg: Int, ink: Int, label: String, onClick: () -> Unit): View =
            FrameLayout(c).apply {
                background = ContextCompat.getDrawable(c, bg)
                contentDescription = label
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                addView(ImageView(c).apply {
                    setImageResource(icon)
                    setColorFilter(ContextCompat.getColor(c, ink))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(dp(c, 30f), dp(c, 30f), Gravity.CENTER))
                layoutParams = LinearLayout.LayoutParams(dp(c, 72f), dp(c, 64f))
            }
    }
}
