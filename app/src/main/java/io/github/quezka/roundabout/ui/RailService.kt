package io.github.quezka.roundabout.ui

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.di.AppGraph
import io.github.quezka.roundabout.domain.Ambient
import io.github.quezka.roundabout.domain.AppCatalog
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.DashClock
import io.github.quezka.roundabout.domain.Role
import io.github.quezka.roundabout.domain.RoleDefaults
import io.github.quezka.roundabout.service.NavAccessibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.Calendar

// always-on rail @ left overscan strip + round corners of app area
// type = TYPE_PHONE <- TYPE_SYSTEM_ERROR ignores NO_LIMITS @ 5.1 -> clamp out of overscan
// plain views ; no compose <- runs all day over every app
class RailService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private val windows = mutableListOf<View>()
    private lateinit var clock: TextView
    private lateinit var battery: TextView
    private lateinit var appColumn: LinearLayout
    private lateinit var appsButton: FrameLayout
    private lateinit var recButton: FrameLayout
    private var wasCharging: Boolean? = null
    private var pal: RailPalette = DARK
    private var themeMode = io.github.quezka.roundabout.domain.ThemeMode.AUTO
    private var lastBattery = ""
    private lateinit var recLabel: TextView
    private var recStarted = 0L
    private var blink = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    // 1 redraw/s ; no animator <- animator = redraw every frame
    private val recTick = object : Runnable {
        override fun run() {
            if (recStarted == 0L) return
            blink = !blink
            val sec = (System.currentTimeMillis() - recStarted) / 1000
            val lens = DashcamService.lenses.value.firstOrNull()
            val tag = when (lens) {
                io.github.quezka.roundabout.domain.Lens.CABIN -> getString(R.string.lens_cabin)
                io.github.quezka.roundabout.domain.Lens.ROAD -> getString(R.string.lens_road)
                null -> ""
            }
            recLabel.text = (if (blink) "● " else "○ ") + "%d:%02d".format(sec / 60, sec % 60) + "\n" + tag
            (recButton.background as GradientDrawable).setColor(if (blink) REC_RED else REC_DIM)
            (recButton.getChildAt(0) as ImageView).drawable.let { (it as GlyphDrawable).setColor(Color.WHITE) }
            main.postDelayed(this, 1000)
        }
    }
    private var railApps: List<AppEntry> = emptyList()
    private var foreground: String? = null
    private var accent: Int = Ambient.ORANGE.argb.toInt()
    private var font: Typeface? = null

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            updateClock()
            applyTheme()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        font = try { ResourcesCompat.getFont(this, R.font.manrope_bold) } catch (e: Exception) { null }
        try {
            addRail()
            addCorners()
        } catch (e: Exception) {
            stopSelf()
            return
        }
        registerReceiver(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        updateClock()
        observe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        main.removeCallbacks(recTick)
        try { unregisterReceiver(tick) } catch (e: Exception) { }
        windows.forEach { try { wm.removeView(it) } catch (e: Exception) { } }
        windows.clear()
        super.onDestroy()
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun screen(): Point = Point().also { wm.defaultDisplay.getRealSize(it) }

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

    private fun addRail() {
        val s = screen()
        val width = railWidthPx(this)
        val root = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply { setColor(pal.panel); cornerRadius = dp(24f).toFloat() }
            setPadding(0, dp(14f), 0, dp(12f))
        }
        root.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(8f), dp(8f), dp(8f), dp(8f))
        })
        clock = text(26f, pal.text)
        battery = text(14f, pal.muted)
        panel.addView(clock)
        panel.addView(battery)
        recLabel = text(13f, REC_RED).apply { visibility = View.GONE }
        panel.addView(recLabel)
        appColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        panel.addView(appColumn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10f) })
        recButton = button(Glyph.REC, pal.high, pal.muted, getString(R.string.dashcam)) { DashcamService.toggle(this) }
        // long press rec: recording -> save event ; idle -> clip viewer
        recButton.setOnLongClickListener {
            if (DashcamService.recording.value && DashcamService.saveEvent()) Unit
            else openDash(SCREEN_CLIPS)
            true
        }
        panel.addView(recButton)
        panel.addView(button(Glyph.BACK, pal.high, pal.text, getString(R.string.back)) { NavAccessibility.back() }.withTopMargin(dp(10f)))
        panel.addView(button(Glyph.HOME, pal.high, pal.text, getString(R.string.home)) { openDash(SCREEN_DASH) }.withTopMargin(dp(10f)))
        appsButton = button(Glyph.GRID, accent, Color.BLACK, getString(R.string.apps)) { openDash(SCREEN_APPS) }
        panel.addView(appsButton.withTopMargin(dp(10f)))
        val lp = overlayParams(width, minOf(s.x, s.y), 0, 0, touchable = true).apply {
            // overlay orientation -> forces landscape over every app ; overscan math needs fixed rotation
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        wm.addView(root, lp)
        windows += root
        pin(root, lp, 0, 0)
    }

    // overlay coords = relative to overscan frame -> measure real spot, shift once
    private fun pin(v: View, lp: WindowManager.LayoutParams, wantX: Int, wantY: Int) {
        v.post {
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

    private fun View.withTopMargin(m: Int): View = also {
        it.layoutParams = LinearLayout.LayoutParams(dp(64f), dp(64f)).apply { topMargin = m }
    }

    private fun addCorners() {
        val s = screen()
        val r = dp(CORNER_DP)
        val left = railWidthPx(this)
        listOf(
            Triple(left, 0, 0), Triple(s.x - r, 0, 1),
            Triple(left, s.y - r, 2), Triple(s.x - r, s.y - r, 3),
        ).forEach { (x, y, which) ->
            val v = CornerView(this, which, pal.bg)
            val lp = overlayParams(r, r, x, y, touchable = false)
            wm.addView(v, lp)
            windows += v
            pin(v, lp, x, y)
        }
    }

    private fun text(sizeSp: Float, color: Int) = TextView(this).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = font ?: Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun button(glyph: Glyph, fill: Int, ink: Int, label: String, onClick: () -> Unit) = FrameLayout(this).apply {
        background = GradientDrawable().apply { setColor(fill); cornerRadius = dp(20f).toFloat() }
        contentDescription = label
        isClickable = true
        setOnClickListener { onClick() }
        addView(ImageView(context).apply { setImageDrawable(GlyphDrawable(glyph, ink)) },
            FrameLayout.LayoutParams(dp(28f), dp(28f), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(64f), dp(64f))
    }

    private fun observe() {
        val g = AppGraph.get(this)
        val apps = g.apps.changes().onStart { emit(Unit) }
            .map { AppCatalog.sorted(g.apps.installed(), packageName) }
            .flowOn(Dispatchers.IO)
        scope.launch {
            combine(apps, g.prefs.rolePackage(Role.NAVIGATION), g.prefs.rolePackage(Role.MUSIC), g.prefs.favorites()) { list, nav, music, pins ->
                (listOfNotNull(
                    RoleDefaults.resolve(Role.NAVIGATION, nav, list),
                    RoleDefaults.resolve(Role.MUSIC, music, list),
                ) + pins.mapNotNull { p -> list.firstOrNull { it.packageName == p } }).distinctBy { it.packageName }.take(5)
            }.collect { railApps = it; renderApps(g) }
        }
        scope.launch {
            combine(g.power.state(), g.dashcamPrefs.autoRecord()) { p, auto -> p to auto }.collect { (p, auto) ->
                lastBattery = (if (p.charging) "⚡" else "") + "${p.percent}%"
                battery.text = lastBattery
                // auto dashcam: plug = car on -> rec ; unplug = car off -> stop
                if (auto && p.charging != wasCharging) {
                    if (p.charging && !DashcamService.recording.value) DashcamService.start(this@RailService)
                    else if (!p.charging && wasCharging == true) DashcamService.stop(this@RailService)
                }
                wasCharging = p.charging
            }
        }
        scope.launch {
            DashcamService.recording.collect { on ->
                applyRec(on)
                recStarted = if (on) System.currentTimeMillis() else 0L
                main.removeCallbacks(recTick)
                if (on) recTick.run()
            }
        }
        scope.launch {
            DashcamService.events.collect {
                android.widget.Toast.makeText(this@RailService, R.string.event_saved, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        scope.launch {
            g.prefs.themeMode().collect { m -> themeMode = m; applyTheme() }
        }
        scope.launch {
            g.prefs.ambient().collect { a ->
                accent = a.argb.toInt()
                (appsButton.background as GradientDrawable).setColor(accent)
                renderApps(g)
            }
        }
        scope.launch {
            NavAccessibility.current.collect { foreground = it; renderApps(g) }
        }
    }

    private fun applyRec(on: Boolean) {
        val img = recButton.getChildAt(0) as ImageView
        (img.drawable as GlyphDrawable).setColor(if (on) Color.WHITE else pal.muted)
        (recButton.background as GradientDrawable).setColor(if (on) REC_RED else pal.high)
        recLabel.visibility = if (on) View.VISIBLE else View.GONE
    }

    // rebuild windows only when light <-> dark flips
    private fun applyTheme() {
        val dark = themeMode.isDark(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        val next = if (dark) DARK else LIGHT
        if (next == pal) return
        pal = next
        windows.forEach { try { wm.removeView(it) } catch (e: Exception) { } }
        windows.clear()
        addRail()
        addCorners()
        updateClock()
        battery.text = lastBattery
        (appsButton.background as GradientDrawable).setColor(accent)
        applyRec(DashcamService.recording.value)
        renderApps(AppGraph.get(this))
    }

    private fun renderApps(g: AppGraph) {
        appColumn.removeAllViews()
        val size = dp(52f)
        railApps.forEach { app ->
            val active = app.packageName == foreground
            val cell = FrameLayout(this).apply {
                background = if (active) GradientDrawable().apply {
                    setColor(Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)))
                    cornerRadius = dp(20f).toFloat()
                } else null
                contentDescription = app.label
                isClickable = true
                setOnClickListener { g.launcher.launch(app) }
            }
            val icon = ImageView(this)
            cell.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            appColumn.addView(cell, LinearLayout.LayoutParams(dp(68f), dp(68f)).apply { topMargin = dp(4f) })
            scope.launch {
                val bmp = kotlinx.coroutines.withContext(Dispatchers.IO) { g.icon(app.packageName, app.activity, size) }
                icon.setImageBitmap(bmp)
            }
        }
    }

    private fun updateClock() {
        val c = Calendar.getInstance()
        clock.text = DashClock.time(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun openDash(screen: String) {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_SCREEN, screen))
    }

    // inverse quarter circle in bg colour -> app area looks rounded
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

    data class RailPalette(val bg: Int, val panel: Int, val high: Int, val text: Int, val muted: Int)

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_DASH = "dash"
        const val SCREEN_APPS = "apps"
        const val SCREEN_CLIPS = "clips"
        const val SCREEN_AA = "aa"
        const val RAIL_DP = 112f
        const val CORNER_DP = 18f
        // same tones as compose scheme ; Theme.kt
        val DARK = RailPalette(Color.rgb(0x0A, 0x0B, 0x0C), Color.rgb(0x15, 0x17, 0x1A), Color.rgb(0x25, 0x28, 0x2D),
            Color.rgb(0xF2, 0xF3, 0xF5), Color.rgb(0xAD, 0xB2, 0xBB))
        val LIGHT = RailPalette(Color.rgb(0xE8, 0xEB, 0xEF), Color.rgb(0xF6, 0xF7, 0xF9), Color.rgb(0xE1, 0xE4, 0xE9),
            Color.rgb(0x15, 0x17, 0x1A), Color.rgb(0x55, 0x5B, 0x66))
        val REC_RED = Color.rgb(0xFF, 0x4A, 0x3D)
        val REC_DIM = Color.rgb(0x8A, 0x26, 0x20)

        fun railWidthPx(context: Context): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, RAIL_DP, context.resources.displayMetrics).toInt()

        fun start(context: Context) {
            try { context.startService(Intent(context, RailService::class.java)) } catch (e: Exception) { }
        }
    }
}
