package com.andrerinas.openheadunit.roundabout.system

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.app.BaseActivity
import com.andrerinas.openheadunit.main.SettingsActivity

// tablet settings = stock settings app replacement @ rom ; catches `android.settings.*`
class TabletSettingsActivity : BaseActivity() {

    private lateinit var ui: RbUi
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val tabs = mutableListOf<TextView>()
    private var panel: Panel? = null
    private var section = Section.WIFI

    enum class Section(val label: Int) {
        WIFI(R.string.rb_sys_wifi), BLUETOOTH(R.string.rb_sys_bt), DISPLAY(R.string.rb_sys_display),
        SOUND(R.string.rb_sys_sound), TIME(R.string.rb_sys_date_time), LANGUAGE(R.string.rb_sys_language),
        APPS(R.string.rb_sys_apps), ACCESS(R.string.rb_sys_access), ABOUT(R.string.rb_sys_about), POWER(R.string.rb_sys_power),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 21) { finish(); return }
        ui = RbUi(this)
        section = savedInstanceState?.getString(KEY)?.let { Section.valueOf(it) } ?: sectionFor(intent)
        setContentView(build())
        show(section)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(sectionFor(intent))
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString(KEY, section.name)
    }

    override fun onDestroy() {
        panel?.stop()
        super.onDestroy()
    }

    private fun build(): LinearLayout {
        val rootView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
        }
        val side = ui.vertical().apply { setPadding(ui.dp(16f), ui.dp(20f), ui.dp(8f), ui.dp(20f)) }
        side.addView(ui.title(getString(R.string.rb_sys_title)).apply { setPadding(ui.dp(12f), 0, 0, ui.dp(12f)) })
        // roundabout app settings first <- most used
        side.addView(tab(getString(R.string.rb_sys_roundabout), false) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        Section.values().forEach { sec ->
            val t = tab(getString(sec.label), false) { show(sec) }
            t.tag = sec
            tabs += t
            side.addView(t)
        }
        rootView.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(side) },
            LinearLayout.LayoutParams(ui.dp(280f), ViewGroup.LayoutParams.MATCH_PARENT))

        content = ui.vertical().apply { setPadding(ui.dp(16f), ui.dp(20f), ui.dp(28f), ui.dp(40f)) }
        scroll = ScrollView(this).apply { addView(content) }
        rootView.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return rootView
    }

    private fun tab(label: String, on: Boolean, onClick: () -> Unit) = ui.text(label, 20f, strong = true).apply {
        gravity = Gravity.CENTER_VERTICAL
        minHeight = ui.dp(60f)
        setPadding(ui.dp(18f), 0, ui.dp(12f), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = ui.dp(4f) }
        paintTab(this, on)
        setOnClickListener { onClick() }
    }

    private fun paintTab(t: TextView, on: Boolean) {
        t.background = ui.touchable(ui.color(if (on) R.color.rb_accent else android.R.color.transparent), 30f)
        t.setTextColor(ui.color(if (on) R.color.rb_on_accent else R.color.rb_text))
    }

    private fun show(sec: Section) {
        section = sec
        tabs.forEach { paintTab(it, it.tag == sec) }
        panel?.stop()
        content.removeAllViews()
        content.addView(ui.title(getString(sec.label)))
        val body = ui.vertical()
        content.addView(body)
        panel = when (sec) {
            Section.WIFI -> WifiPanel(ui)
            Section.BLUETOOTH -> BluetoothPanel(ui)
            Section.DISPLAY -> DisplayPanel(ui)
            Section.SOUND -> SoundPanel(ui)
            Section.TIME -> TimePanel(ui)
            Section.LANGUAGE -> LanguagePanel(ui) { recreate() }
            Section.APPS -> AppsPanel(ui)
            Section.ACCESS -> AccessPanel(ui)
            Section.ABOUT -> AboutPanel(ui)
            Section.POWER -> PowerPanel(ui)
        }.also { it.attach(body) }
        scroll.scrollTo(0, 0)
    }

    companion object {
        private const val KEY = "rb-tablet-section"

        fun sectionFor(intent: Intent?): Section = when (intent?.action) {
            Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIFI_IP_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
            "android.net.wifi.PICK_WIFI_NETWORK" -> Section.WIFI
            Settings.ACTION_BLUETOOTH_SETTINGS -> Section.BLUETOOTH
            Settings.ACTION_DISPLAY_SETTINGS -> Section.DISPLAY
            Settings.ACTION_SOUND_SETTINGS -> Section.SOUND
            Settings.ACTION_DATE_SETTINGS -> Section.TIME
            Settings.ACTION_LOCALE_SETTINGS, Settings.ACTION_INPUT_METHOD_SETTINGS -> Section.LANGUAGE
            Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
            Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> Section.APPS
            Settings.ACTION_ACCESSIBILITY_SETTINGS, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            "android.settings.USAGE_ACCESS_SETTINGS", Settings.ACTION_SECURITY_SETTINGS,
            "android.settings.action.MANAGE_OVERLAY_PERMISSION", "android.settings.action.MANAGE_WRITE_SETTINGS" -> Section.ACCESS
            Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Settings.ACTION_MEMORY_CARD_SETTINGS -> Section.ABOUT
            else -> Section.WIFI
        }
    }
}
