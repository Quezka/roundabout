package com.andrerinas.openheadunit.roundabout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.main.FavoriteTarget

// roundabout rail @ home left edge: clock, battery, aa mark, favorite apps, settings, all apps
// replaces oh driver bar ; HomeFragment owns actions -> passed in as callbacks
// no rail in layout (portrait) -> every call = no-op
class RoundaboutRail(
    root: View,
    private val onAndroidAuto: () -> Unit,
    private val onFavorite: (slot: Int) -> Unit,
    private val onFavoriteLong: (slot: Int, anchor: View) -> Boolean,
    private val onSettings: () -> Unit,
    private val onAllApps: () -> Unit,
    private val onExit: () -> Unit,
) {
    private val ctx = root.context
    private val rail: View? = root.findViewById(R.id.rb_rail)
    private val clock: TextView? = root.findViewById(R.id.rb_rail_clock)
    private val battery: TextView? = root.findViewById(R.id.rb_rail_battery)
    private val apps: LinearLayout? = root.findViewById(R.id.rb_rail_apps)
    private val aaRing: View? = root.findViewById(R.id.rb_rail_aa_ring)
    private val exit: View? = root.findViewById(R.id.rb_rail_exit)
    private var batteryReceiver: BroadcastReceiver? = null

    val present: Boolean get() = rail != null

    init {
        root.findViewById<View>(R.id.rb_rail_aa)?.setOnClickListener { onAndroidAuto() }
        root.findViewById<View>(R.id.rb_rail_settings)?.setOnClickListener { onSettings() }
        root.findViewById<View>(R.id.rb_rail_all)?.setOnClickListener { onAllApps() }
        exit?.setOnClickListener { onExit() }
    }

    fun start() {
        if (rail == null || batteryReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = renderBattery(i)
        }
        batteryReceiver = receiver
        // sticky -> first value now
        ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { renderBattery(it) }
    }

    fun stop() {
        batteryReceiver?.let { try { ctx.unregisterReceiver(it) } catch (e: Exception) { } }
        batteryReceiver = null
    }

    fun renderClock(time: String) {
        clock?.text = time
    }

    fun renderExit(shown: Boolean) {
        exit?.visibility = if (shown) View.VISIBLE else View.GONE
    }

    // live session -> accent ring ; tap = back to projection
    fun renderSession(active: Boolean) {
        aaRing?.visibility = if (active) View.VISIBLE else View.INVISIBLE
    }

    // filled slots = app icons ; first free slot = "+" ; rest hidden
    fun renderFavorites(targets: List<FavoriteTarget>, shown: Boolean) {
        val column = apps ?: return
        column.removeAllViews()
        if (!shown) return
        val pm = ctx.packageManager
        var plusShown = false
        targets.forEachIndexed { slot, target ->
            val icon = iconOf(pm, target)
            when {
                icon != null -> column.addView(slotView(icon, label(pm, target), slot, filled = true))
                !plusShown -> {
                    plusShown = true
                    val plus = ContextCompat.getDrawable(ctx, R.drawable.ic_add_white)?.let { DrawableCompat.wrap(it.mutate()) }?.apply {
                        DrawableCompat.setTint(this, ContextCompat.getColor(ctx, R.color.rb_muted))
                    } ?: return@forEachIndexed
                    column.addView(slotView(plus, ctx.getString(R.string.rb_add_app), slot, filled = false))
                }
            }
        }
    }

    private fun slotView(icon: Drawable, label: CharSequence, slot: Int, filled: Boolean): View {
        val size = dp(64f)
        return FrameLayout(ctx).apply {
            background = ContextCompat.getDrawable(ctx, R.drawable.rb_rail_app)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onFavorite(slot) }
            if (filled) setOnLongClickListener { onFavoriteLong(slot, it) }
            val iconPx = if (filled) dp(48f) else dp(28f)
            addView(ImageView(ctx).apply {
                setImageDrawable(icon)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(size, size).apply { topMargin = dp(4f) }
        }
    }

    private fun packageOf(target: FavoriteTarget): String? = when (target) {
        is FavoriteTarget.App -> target.packageName
        is FavoriteTarget.Shortcut -> target.packageName
        FavoriteTarget.Empty -> null
    }

    private fun iconOf(pm: PackageManager, target: FavoriteTarget): Drawable? {
        val pkg = packageOf(target) ?: return null
        return try { pm.getApplicationIcon(pkg) } catch (e: PackageManager.NameNotFoundException) { null }
    }

    private fun label(pm: PackageManager, target: FavoriteTarget): CharSequence {
        val pkg = packageOf(target) ?: return ""
        return try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) } catch (e: Exception) { pkg }
    }

    private fun renderBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        battery?.text = (if (charging) "⚡" else "") + "${level * 100 / scale}%"
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()
}
