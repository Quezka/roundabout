package com.andrerinas.openheadunit.roundabout

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapProjectionActivity
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.main.AppLauncher
import com.andrerinas.openheadunit.main.FavoriteAppsPolicy
import com.andrerinas.openheadunit.main.FavoriteTarget
import com.andrerinas.openheadunit.main.MainActivity
import com.andrerinas.openheadunit.utils.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// always-on roundabout rail over every app, in the left overscan strip
// overscan (root `wm overscan`) => apps + android auto laid out beside it, never under it
// buttons follow the screen: home -> all apps ; android auto -> its menu ; other app -> back
// + own volume panel <- systemui off => no system volume popup
class RoundaboutRailService : Service() {
    private enum class Place { HOME, PROJECTION, OURS, OTHER }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private val windows = mutableListOf<View>()
    private var font: Typeface? = null

    private lateinit var battery: TextView
    private lateinit var apps: LinearLayout
    private lateinit var aaRing: View
    private lateinit var contextButton: FrameLayout
    private lateinit var contextIcon: ImageView

    private var volumePanel: View? = null
    // setup running -> rail = clock only <- no way out of setup half done
    private var railPanel: LinearLayout? = null
    private val hiddenForSetup = mutableListOf<View>()
    private var volumeBar: SeekBar? = null
    private var volumeText: TextView? = null
    private var brightBar: SeekBar? = null
    private var brightText: TextView? = null
    private val hideVolume = Runnable { volumePanel?.visibility = View.GONE }

    private val ourTop: Activity? get() = resumed?.get()
    private var lastVolume = -1
    private var place = Place.OTHER
    private var live = false


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> renderBattery(i)
                // only a real change shows the panel <- android auto re-sends the same level often
                VOLUME_CHANGED -> if (i.getIntExtra(EXTRA_STREAM, -1) == AudioManager.STREAM_MUSIC) {
                    val v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (v != lastVolume) { lastVolume = v; showVolume() }
                }
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == FAVORITES_KEY) renderApps()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        font = try { ResourcesCompat.getFont(this, R.font.manrope_bold) } catch (e: Exception) { null }
        applyOverscan(this)
        try {
            addRail()
            addCorners()
            addVolumePanel()
        } catch (e: Exception) {
            stopSelf()
            return
        }
        instance = this
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(VOLUME_CHANGED)
        })?.let { if (it.action == Intent.ACTION_BATTERY_CHANGED) renderBattery(it) }
        getSharedPreferences(Settings.PREFS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        renderApps()
        scope.launch {
            App.provide(this@RoundaboutRailService).commManager.connectionState.collect {
                live = it is CommManager.ConnectionState.HandshakeComplete || it is CommManager.ConnectionState.TransportStarted
                aaRing.visibility = if (live) View.VISIBLE else View.INVISIBLE
            }
        }
        scope.launch { NavAccessibility.current.collect { renderPlace() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (e: Exception) { }
        if (instance === this) instance = null
        getSharedPreferences(Settings.PREFS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        windows.forEach { try { wm.removeView(it) } catch (e: Exception) { } }
        windows.clear()
        super.onDestroy()
    }

    // --- windows ---

    private fun dp(v: Float): Int = dp(this, v)

    // landscape always (rotation locked) ; service may start while still portrait -> sort sides
    private fun screen(): Point = Point().also {
        wm.defaultDisplay.getRealSize(it)
        val long = maxOf(it.x, it.y)
        val short = minOf(it.x, it.y)
        it.set(long, short)
    }

    @Suppress("DEPRECATION")
    private fun overlayParams(w: Int, h: Int, x: Int, y: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private data class Pinned(val v: View, val lp: WindowManager.LayoutParams, val x: Int, val y: Int)
    private val pinned = mutableListOf<Pinned>()
    // wm moves overlays on rotation / overscan changes -> check spots every 2 s
    private val repin = object : Runnable {
        override fun run() {
            pinned.forEach { place(it.v, it.lp, it.x, it.y) }
            main.postDelayed(this, 2000)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        main.postDelayed({ pinned.forEach { place(it.v, it.lp, it.x, it.y) } }, 300)
    }

    // overlay coords = relative to the overscan frame -> measure the real spot, shift
    private fun pin(v: View, lp: WindowManager.LayoutParams, wantX: Int, wantY: Int) {
        pinned += Pinned(v, lp, wantX, wantY)
        if (pinned.size == 1) main.postDelayed(repin, 2000)
        place(v, lp, wantX, wantY)
    }

    private fun place(v: View, lp: WindowManager.LayoutParams, wantX: Int, wantY: Int) {
        v.post {
            if (v.windowToken == null) return@post
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val dx = wantX - loc[0]
            val dy = wantY - loc[1]
            if (dx != 0 || dy != 0) {
                lp.x += dx
                lp.y += dy
                try { wm.updateViewLayout(v, lp) } catch (e: Exception) { }
            }
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun addRail() {
        val s = screen()
        val width = railPx(this)
        // strip + corners = black, same as the app windows beside them
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(context, R.drawable.rb_rail_bg)
            setPadding(0, dp(14f), 0, dp(12f))
        }
        railPanel = panel
        root.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(8f), dp(8f), dp(8f), dp(8f))
        })
        panel.addView(TextClock(this).apply {
            setTextColor(color(R.color.rb_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = font ?: Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
        })
        battery = TextView(this).apply {
            setTextColor(color(R.color.rb_muted))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = font ?: Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        panel.addView(battery, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })

        // android auto: rounded tile, accent outline while a session is live
        val aa = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(context, R.drawable.rb_rail_btn)
            contentDescription = getString(R.string.rb_android_auto)
            isClickable = true
            setOnClickListener { openAndroidAuto() }
        }
        aa.addView(ImageView(this).apply {
            setImageResource(R.drawable.rb_ic_aa)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(44f), dp(44f), Gravity.CENTER))
        aaRing = ImageView(this).apply {
            setImageResource(R.drawable.rb_aa_ring)
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.INVISIBLE
        }
        aa.addView(aaRing, FrameLayout.LayoutParams(-1, -1))
        panel.addView(aa, LinearLayout.LayoutParams(dp(76f), dp(64f)).apply { topMargin = dp(12f) })

        panel.addView(View(this).apply { setBackgroundColor(color(R.color.rb_container_highest)) },
            LinearLayout.LayoutParams(dp(40f), dp(2f)).apply { topMargin = dp(10f) })
        apps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        panel.addView(apps, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4f) })

        val gear = ImageView(this).apply { setImageResource(R.drawable.ic_settings_white); setColorFilter(color(R.color.rb_text)) }
        panel.addView(button(gear, R.drawable.rb_rail_btn) { openSettings() }.also {
            it.contentDescription = getString(R.string.settings)
        })
        contextIcon = ImageView(this)
        contextButton = button(contextIcon, R.drawable.rb_rail_btn) { onContextButton() }
        panel.addView(contextButton.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(6f) })
        val volIcon = ImageView(this).apply { setImageResource(R.drawable.rb_ic_volume); setColorFilter(color(R.color.rb_text)) }
        panel.addView(button(volIcon, R.drawable.rb_rail_btn) { toggleVolume() }.also {
            it.contentDescription = getString(R.string.rb_volume)
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(6f)
        })
        val homeIcon = ImageView(this).apply { setImageResource(R.drawable.rb_ic_home); setColorFilter(color(R.color.rb_on_accent)) }
        panel.addView(button(homeIcon, R.drawable.rb_rail_btn_accent) { openHome() }.also {
            it.contentDescription = getString(R.string.rb_exit_home)
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(6f)
        })
        renderPlace()

        val lp = overlayParams(width, minOf(s.x, s.y), 0, 0, touchable = true).apply {
            // overscan math needs a fixed rotation
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        wm.addView(root, lp)
        windows += root
        pin(root, lp, 0, 0)
    }

    private fun button(icon: ImageView, bg: Int, onClick: () -> Unit) = FrameLayout(this).apply {
        background = ContextCompat.getDrawable(context, bg)
        isClickable = true
        setOnClickListener { onClick() }
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(icon, FrameLayout.LayoutParams(dp(28f), dp(28f), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(72f), dp(48f))
    }

    // rounded corners of the app area, as on the home cards
    private fun addCorners() {
        val s = screen()
        val r = dp(CORNER_DP)
        val left = railPx(this)
        listOf(
            Triple(left, 0, 0), Triple(s.x - r, 0, 1),
            Triple(left, s.y - r, 2), Triple(s.x - r, s.y - r, 3),
        ).forEach { (x, y, which) ->
            val v = CornerView(this, which, Color.BLACK)
            val lp = overlayParams(r, r, x, y, touchable = false)
            wm.addView(v, lp)
            windows += v
            pin(v, lp, x, y)
        }
    }

    // volume + brightness ; opens from the rail, a volume key or a volume change ; hides by itself
    private fun addVolumePanel() {
        val s = screen()
        val w = dp(480f)
        val h = dp(150f)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(color(R.color.rb_container_high))
                cornerRadius = dp(28f).toFloat()
            }
            setPadding(dp(22f), dp(10f), dp(22f), dp(10f))
            visibility = View.GONE
        }
        val vol = sliderRow(root, R.drawable.rb_ic_volume, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) { value ->
            lastVolume = value
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            percent(value)
        }
        val light = sliderRow(root, R.drawable.rb_ic_brightness, 255 - MIN_BRIGHTNESS) { value ->
            setBrightness(value + MIN_BRIGHTNESS)
            "${value * 100 / (255 - MIN_BRIGHTNESS)}%"
        }
        val left = railPx(this)
        val x = left + (s.x - left - w) / 2
        val y = s.y - h - dp(24f)
        val lp = overlayParams(w, h, x, y, touchable = true)
        wm.addView(root, lp)
        windows += root
        pin(root, lp, x, y)
        volumePanel = root
        volumeBar = vol.first
        volumeText = vol.second
        brightBar = light.first
        brightText = light.second
    }

    private fun sliderRow(parent: LinearLayout, icon: Int, max: Int, onUser: (Int) -> String): Pair<SeekBar, TextView> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(color(R.color.rb_text))
        }, LinearLayout.LayoutParams(dp(28f), dp(28f)))
        val text = TextView(this).apply {
            setTextColor(color(R.color.rb_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = font ?: Typeface.DEFAULT_BOLD
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val bar = SeekBar(this).apply {
            this.max = max
            progressDrawable = ContextCompat.getDrawable(context, R.drawable.rb_volume_track)
            thumb = ContextCompat.getDrawable(context, R.drawable.rb_volume_thumb)
            splitTrack = false
            setPadding(dp(18f), 0, dp(18f), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) { text.text = onUser(value); keepVolume() }
                }
                override fun onStartTrackingTouch(sb: SeekBar) { main.removeCallbacks(hideVolume) }
                override fun onStopTrackingTouch(sb: SeekBar) { keepVolume() }
            })
        }
        // bar height = thumb height ; track inset 8dp each side => knob + track share one centre
        row.addView(bar, LinearLayout.LayoutParams(0, dp(28f), 1f))
        row.addView(text, LinearLayout.LayoutParams(dp(58f), -2))
        parent.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        return bar to text
    }

    private fun brightness(): Int = try {
        AndroidSettings.System.getInt(contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS)
    } catch (e: Exception) { 180 }

    private fun setBrightness(value: Int) {
        try {
            AndroidSettings.System.putInt(contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS_MODE,
                AndroidSettings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            AndroidSettings.System.putInt(contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS, value.coerceIn(MIN_BRIGHTNESS, 255))
        } catch (e: Exception) { }
    }

    // one key press = one step of music volume ; panel shows the result
    fun step(up: Boolean) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        showVolume()
    }

    // --- state ---

    private fun percent(v: Int): String {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return "${v * 100 / max}%"
    }

    private fun showVolume() {
        val v = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        lastVolume = v
        volumeBar?.progress = v
        volumeText?.text = percent(v)
        val b = (brightness() - MIN_BRIGHTNESS).coerceAtLeast(0)
        brightBar?.progress = b
        brightText?.text = "${b * 100 / (255 - MIN_BRIGHTNESS)}%"
        volumePanel?.visibility = View.VISIBLE
        keepVolume()
    }

    private fun keepVolume() {
        main.removeCallbacks(hideVolume)
        main.postDelayed(hideVolume, VOLUME_HIDE_MS)
    }

    private fun toggleVolume() {
        if (volumePanel?.visibility == View.VISIBLE) hideVolume.run() else showVolume()
    }

    private fun renderBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        battery.text = (if (charging) "⚡" else "") + "${level * 100 / scale}%"
    }

    private fun renderPlace() {
        if (!::contextButton.isInitialized) return
        val top = ourTop
        place = when {
            top is AapProjectionActivity -> Place.PROJECTION
            top is MainActivity -> Place.HOME
            top != null -> Place.OURS
            NavAccessibility.current.value == packageName -> Place.OURS
            else -> Place.OTHER
        }
        val (icon, label) = when (place) {
            Place.HOME -> R.drawable.ic_apps to R.string.home_all_apps
            Place.PROJECTION -> R.drawable.rb_ic_aa_menu to R.string.exit_dialog_title
            Place.OURS, Place.OTHER -> R.drawable.rb_ic_back to R.string.back
        }
        val setup = top is com.andrerinas.openheadunit.roundabout.setup.SetupActivity
        railPanel?.let { p ->
            if (setup && hiddenForSetup.isEmpty()) {
                for (i in 1 until p.childCount) p.getChildAt(i).takeIf { it.visibility == View.VISIBLE }?.let {
                    it.visibility = View.INVISIBLE
                    hiddenForSetup += it
                }
            } else if (!setup && hiddenForSetup.isNotEmpty()) {
                hiddenForSetup.forEach { it.visibility = View.VISIBLE }
                hiddenForSetup.clear()
            }
        }
        contextIcon.setImageResource(icon)
        contextIcon.setColorFilter(color(R.color.rb_text))
        contextButton.contentDescription = getString(label)
        renderApps()
    }

    private fun renderApps() {
        if (!::apps.isInitialized) return
        apps.removeAllViews()
        val targets = Settings(this).homeFavoriteApps
        val pm = packageManager
        val foreground = if (ourTop != null) packageName else NavAccessibility.current.value
        var plusShown = false
        targets.forEachIndexed { slot, target ->
            val pkg = when (target) {
                is FavoriteTarget.App -> target.packageName
                is FavoriteTarget.Shortcut -> target.packageName
                FavoriteTarget.Empty -> null
            }
            val icon = pkg?.let { try { pm.getApplicationIcon(it) } catch (e: PackageManager.NameNotFoundException) { null } }
            if (icon == null && plusShown) return@forEachIndexed
            val cell = FrameLayout(this).apply {
                background = if (pkg != null && pkg == foreground) GradientDrawable().apply {
                    setColor(Color.argb(70, 0xFF, 0x7A, 0x1A))
                    cornerRadius = dp(18f).toFloat()
                } else ContextCompat.getDrawable(context, R.drawable.rb_rail_app)
                isClickable = true
            }
            if (icon != null) {
                cell.contentDescription = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg!!, 0)) } catch (e: Exception) { pkg }
                cell.setOnClickListener { AppLauncher.launch(this, target) }
                cell.setOnLongClickListener { favoriteMenu(slot, it); true }
                cell.addView(ImageView(this).apply { setImageDrawable(icon) }, FrameLayout.LayoutParams(dp(44f), dp(44f), Gravity.CENTER))
            } else {
                plusShown = true
                cell.contentDescription = getString(R.string.rb_add_app)
                cell.setOnClickListener { RoundaboutBus.pickFavoriteSlot = slot; openHome() }
                cell.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_add_white)
                    setColorFilter(color(R.color.rb_muted))
                }, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
            }
            apps.addView(cell, LinearLayout.LayoutParams(dp(60f), dp(52f)).apply { topMargin = dp(2f) })
        }
    }

    private fun favoriteMenu(slot: Int, anchor: View) {
        try {
            PopupMenu(this, anchor).apply {
                menu.add(R.string.home_favorite_change).setOnMenuItemClickListener {
                    RoundaboutBus.pickFavoriteSlot = slot
                    openHome()
                    true
                }
                menu.add(R.string.remove).setOnMenuItemClickListener {
                    val s = Settings(this@RoundaboutRailService)
                    s.homeFavoriteApps = FavoriteAppsPolicy.remove(s.homeFavoriteApps, slot)
                    true
                }
                show()
            }
        } catch (e: Exception) {
            // no window token for a popup here -> drawer picks the slot instead
            RoundaboutBus.pickFavoriteSlot = slot
            openHome()
        }
    }

    // --- actions ---

    private fun onContextButton() {
        com.andrerinas.openheadunit.utils.AppLog.i("RoundaboutRail: context button, place=$place, menu=${RoundaboutBus.projectionMenu != null}")
        when (place) {
            Place.HOME -> { RoundaboutBus.openDrawer = true; openHome() }
            Place.PROJECTION -> RoundaboutBus.projectionMenu?.invoke()
                ?: startActivity(Intent(this, AapProjectionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_EXIT_MENU, true))
            Place.OURS, Place.OTHER -> if (!NavAccessibility.back()) openAccessibilitySettings()
        }
    }

    private fun openAndroidAuto() {
        if (live) {
            startActivity(Intent(this, AapProjectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            RoundaboutBus.startAndroidAuto = true
            openHome()
        }
    }

    private fun openHome() {
        hideVolume.run()
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openSettings() {
        hideVolume.run()
        startActivity(Intent(this, com.andrerinas.openheadunit.main.SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    private class CornerView(context: Context, private val which: Int, bg: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = if (which == 0 || which == 2) w else 0f
            val cy = if (which == 0 || which == 1) h else 0f
            val path = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(0f, 0f, w, h, Path.Direction.CW)
                addOval(RectF(cx - w, cy - h, cx + w, cy + h), Path.Direction.CW)
            }
            canvas.drawPath(path, paint)
        }
    }

    companion object {
        const val RAIL_DP = 112f
        private const val CORNER_DP = 18f
        private const val VOLUME_HIDE_MS = 3000L
        private const val FAVORITES_KEY = "home-favorite-apps"
        // hidden framework broadcast ; still sent on 5.x
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_EXIT_MENU = "rb_exit_menu"
        private const val MIN_BRIGHTNESS = 10
        private const val OVERSCAN_KEY = "rb-overscan-applied"

        val projectionOnTop: Boolean get() = resumed?.get() is AapProjectionActivity

        // volume key from NavAccessibility ; no rail running -> plain step
        fun volumeStep(context: Context, up: Boolean) {
            val s = instance
            if (s != null) { s.main.post { s.step(up) }; return }
            (context.getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(
                AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        }

        @Volatile var running = false
            private set
        @Volatile private var instance: RoundaboutRailService? = null
        // own resumed activity <- App lifecycle callbacks (also before the service exists)
        @Volatile private var resumed: java.lang.ref.WeakReference<Activity>? = null

        fun onOurActivity(now: Activity?, paused: Activity? = null) {
            if (now != null) resumed = java.lang.ref.WeakReference(now)
            else if (resumed?.get() === paused) resumed = null
            instance?.let { s -> s.main.postDelayed({ s.renderPlace() }, if (now != null) 0L else 300L) }
        }

        private fun dp(c: Context, v: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

        fun railPx(c: Context): Int = dp(c, RAIL_DP)

        fun start(context: Context) {
            try { context.startService(Intent(context, RoundaboutRailService::class.java)) } catch (e: Exception) { }
        }

        // landscape left = natural top (rotation 90) | bottom (270) ; root only, else rail overlaps apps
        fun applyOverscan(context: Context) {
            val wm = context.getSystemService(WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            val rotation = wm.defaultDisplay.rotation
            val px = railPx(context)
            val box = when (rotation) {
                Surface.ROTATION_90 -> intArrayOf(0, px, 0, 0)
                Surface.ROTATION_270 -> intArrayOf(0, 0, 0, px)
                // portrait (boot, before landscape lock) -> keep what is set
                else -> return
            }
            val spec = box.joinToString(",")
            // once per spec <- overscan survives reboots ; rom = priv-app api, no su
            val prefs = context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(OVERSCAN_KEY, null) == spec) return
            Thread {
                try {
                    val ok = com.andrerinas.openheadunit.roundabout.system.SystemAccess.setOverscan(box[0], box[1], box[2], box[3])
                    if (ok) prefs.edit().putString(OVERSCAN_KEY, spec).apply()
                } catch (e: Exception) { }
            }.start()
        }
    }
}
