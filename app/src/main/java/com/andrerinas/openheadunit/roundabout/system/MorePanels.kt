// tablet only (5.1) ; activities stop < api 21
@file:android.annotation.SuppressLint("NewApi")

package com.andrerinas.openheadunit.roundabout.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.widget.ImageView
import android.widget.LinearLayout
import com.andrerinas.openheadunit.BuildConfig
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.roundabout.setup.SetupActivity

class DisplayPanel(ui: RbUi) : Panel(ui) {
    private fun system(key: String, def: Int) = Settings.System.getInt(context.contentResolver, key, def)
    private fun global(key: String, def: Int) = Settings.Global.getInt(context.contentResolver, key, def)

    override fun render() {
        root.removeAllViews()
        root.addView(ui.slider(s(R.string.rb_sys_brightness), 255 - MIN_BRIGHTNESS,
            system(Settings.System.SCREEN_BRIGHTNESS, 160) - MIN_BRIGHTNESS) { v ->
            val r = context.contentResolver
            try {
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, v + MIN_BRIGHTNESS)
            } catch (e: Exception) { }
        })

        root.addView(ui.header(s(R.string.rb_sys_screen_off)))
        val timeout = system(Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
        val idx = TIMEOUTS.indexOfFirst { it >= timeout }.let { if (it < 0) TIMEOUTS.lastIndex else it }
        root.addView(ui.choice(listOf(s(R.string.rb_sys_1min), s(R.string.rb_sys_5min), s(R.string.rb_sys_30min), s(R.string.rb_sys_never)), idx) { i ->
            bg({ SystemAccess.putSystem(context, Settings.System.SCREEN_OFF_TIMEOUT, TIMEOUTS[i].toString()) }, {})
        })
        val stayOn = global(Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0
        root.addView(ui.toggle(s(R.string.rb_sys_stay_on), s(R.string.rb_sys_stay_on_sub), stayOn) { on ->
            bg({ SystemAccess.putGlobal(context, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (on) "7" else "0") }, {})
        })
        val ohSettings = com.andrerinas.openheadunit.utils.Settings(context)
        root.addView(ui.toggle(s(R.string.rb_sys_power_off_screen), s(R.string.rb_sys_power_off_screen_sub), ohSettings.externalPowerScreenOff) { on ->
            ohSettings.externalPowerScreenOff = on
        })

        root.addView(ui.header(s(R.string.rb_sys_animations)))
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        val sel = when { scale == 0f -> 0; scale < 1f -> 1; else -> 2 }
        root.addView(ui.choice(listOf(s(R.string.rb_sys_anim_off), s(R.string.rb_sys_anim_fast), s(R.string.rb_sys_anim_normal)), sel) { i ->
            val v = listOf("0", "0.5", "1")[i]
            bg({ animations(context, v) }, {})
        })
        root.addView(ui.note(s(R.string.rb_sys_anim_note)))
    }

    companion object {
        const val MIN_BRIGHTNESS = 10
        val TIMEOUTS = listOf(60_000, 300_000, 1_800_000, Int.MAX_VALUE)

        fun animations(context: Context, v: String) {
            SystemAccess.putGlobal(context, Settings.Global.WINDOW_ANIMATION_SCALE, v)
            SystemAccess.putGlobal(context, Settings.Global.TRANSITION_ANIMATION_SCALE, v)
            SystemAccess.putGlobal(context, Settings.Global.ANIMATOR_DURATION_SCALE, v)
        }
    }
}

class SoundPanel(ui: RbUi) : Panel(ui) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun render() {
        root.removeAllViews()
        STREAMS.forEach { (stream, label) ->
            root.addView(ui.slider(s(label), audio.getStreamMaxVolume(stream), audio.getStreamVolume(stream)) { v ->
                audio.setStreamVolume(stream, v, 0)
            })
        }
        val touch = Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1
        root.addView(ui.toggle(s(R.string.rb_sys_touch_sounds), null, touch) { on ->
            bg({
                SystemAccess.putSystem(context, Settings.System.SOUND_EFFECTS_ENABLED, if (on) "1" else "0")
                if (on) audio.loadSoundEffects() else audio.unloadSoundEffects()
            }, {})
        })
    }

    companion object {
        val STREAMS = listOf(
            AudioManager.STREAM_MUSIC to R.string.rb_sys_vol_media,
            AudioManager.STREAM_NOTIFICATION to R.string.rb_sys_vol_notify,
            AudioManager.STREAM_VOICE_CALL to R.string.rb_sys_vol_call,
            AudioManager.STREAM_ALARM to R.string.rb_sys_vol_alarm,
            AudioManager.STREAM_SYSTEM to R.string.rb_sys_vol_system,
        )
    }
}

class AppsPanel(ui: RbUi) : Panel(ui) {
    private class Entry(val pkg: String, val label: String, val icon: Drawable?, val system: Boolean, val enabled: Boolean)

    private var showSystem = false
    private var entries: List<Entry>? = null

    override fun start() = load()

    private fun load() {
        entries = null
        bg({
            val pm = context.packageManager
            entries = pm.getInstalledApplications(PackageManager.GET_DISABLED_COMPONENTS)
                .filter { it.packageName != context.packageName }
                .map { ai ->
                    Entry(ai.packageName, ai.loadLabel(pm).toString(), try { ai.loadIcon(pm) } catch (e: Exception) { null },
                        ai.flags and ApplicationInfo.FLAG_SYSTEM != 0, ai.enabled)
                }
                .sortedBy { it.label.lowercase() }
        })
    }

    override fun render() {
        root.removeAllViews()
        root.addView(ui.choice(listOf(s(R.string.rb_sys_apps_installed), s(R.string.rb_sys_apps_system)), if (showSystem) 1 else 0) { i ->
            showSystem = i == 1
            render()
        })
        val list = entries
        if (list == null) {
            root.addView(ui.note(s(R.string.rb_sys_loading)))
            return
        }
        list.filter { it.system == showSystem }.forEach { e ->
            val sub = if (e.enabled) e.pkg else s(R.string.rb_sys_app_disabled, e.pkg)
            val row = ui.row(e.label, sub, ui.chevron()) { actions(e) }
            e.icon?.let { icon ->
                row.view.addView(ImageView(context).apply {
                    setImageDrawable(icon)
                    layoutParams = LinearLayout.LayoutParams(ui.dp(44f), ui.dp(44f)).apply { rightMargin = ui.dp(16f) }
                }, 0)
            }
            if (!e.enabled) row.view.alpha = 0.55f
            root.addView(row.view)
        }
    }

    private fun actions(e: Entry) {
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(e.pkg)
        val opts = mutableListOf<Pair<Int, () -> Unit>>()
        if (launch != null && e.enabled) opts += R.string.rb_sys_app_open to {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        opts += R.string.rb_sys_app_stop to { bg({ SystemAccess.forceStop(context, e.pkg) }, {}) }
        opts += R.string.rb_sys_app_clear to {
            ui.ask(e.label, s(R.string.rb_sys_app_clear_ask), s(R.string.rb_sys_app_clear)) {
                bg({ SystemAccess.clearData(context, e.pkg) }, {})
            }
        }
        opts += (if (e.enabled) R.string.rb_sys_app_disable else R.string.rb_sys_app_enable) to {
            bg({ SystemAccess.setEnabled(context, e.pkg, !e.enabled) }, { load() })
        }
        if (!e.system) opts += R.string.rb_sys_app_uninstall to {
            ui.ask(e.label, s(R.string.rb_sys_app_uninstall_ask), s(R.string.rb_sys_app_uninstall)) {
                bg({ SystemAccess.uninstall(context, e.pkg); Thread.sleep(1500) }, { load() })
            }
        }
        ui.pick(e.label, opts.map { s(it.first) }) { i -> opts[i].second() }
    }
}

class AccessPanel(ui: RbUi) : Panel(ui) {
    override fun render() {
        root.removeAllViews()
        root.addView(ui.note(s(R.string.rb_sys_access_note)))
        val items = listOf(
            R.string.rb_sys_access_system to SystemAccess.privileged(context),
            R.string.rb_sys_access_media to SystemAccess.listenerOn(context),
            R.string.rb_sys_access_keys to SystemAccess.accessibilityOn(context),
            R.string.rb_sys_access_admin to SystemAccess.adminOn(context),
            R.string.rb_sys_access_home to isHome(context),
        )
        items.forEach { (label, on) ->
            val mark = ui.value(if (on) "✓" else "✕").apply { setTextColor(ui.color(if (on) R.color.rb_accent else R.color.rb_muted)) }
            root.addView(ui.row(s(label), s(if (on) R.string.rb_sys_on else R.string.rb_sys_off), mark).view)
        }
        root.addView(ui.spacer(12f))
        root.addView(ui.button(s(R.string.rb_sys_grant_all)) { bg({ SystemAccess.grantAll(context) }) })
    }

    companion object {
        fun isHome(context: Context): Boolean {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName
        }
    }
}

class AboutPanel(ui: RbUi) : Panel(ui) {
    override fun render() {
        root.removeAllViews()
        val mem = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val data = Environment.getDataDirectory()
        val free = data.usableSpace
        val total = data.totalSpace
        val up = SystemClock.elapsedRealtime() / 60_000
        listOf(
            s(R.string.rb_sys_about_app) to "Roundabout ${BuildConfig.VERSION_NAME}",
            s(R.string.rb_sys_about_model) to "${Build.MANUFACTURER} ${Build.MODEL}",
            s(R.string.rb_sys_about_android) to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            s(R.string.rb_sys_about_build) to Build.DISPLAY,
            s(R.string.rb_sys_about_kernel) to (System.getProperty("os.version") ?: "?"),
            s(R.string.rb_sys_about_ram) to gb(mem.totalMem),
            s(R.string.rb_sys_about_storage) to s(R.string.rb_sys_about_storage_val, gb(free), gb(total)),
            s(R.string.rb_sys_about_uptime) to String.format("%d:%02d", up / 60, up % 60),
        ).forEach { (k, v) -> root.addView(ui.row(k, v).view) }

        root.addView(ui.header(s(R.string.rb_sys_developer)))
        val adb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        root.addView(ui.toggle(s(R.string.rb_sys_adb), s(R.string.rb_sys_adb_sub), adb) { on ->
            bg({ SystemAccess.putGlobal(context, Settings.Global.ADB_ENABLED, if (on) "1" else "0") }, {})
        })
        root.addView(ui.row(s(R.string.rb_sys_setup_again), null, ui.chevron()) {
            context.startActivity(Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.view)
        root.addView(ui.row(s(R.string.rb_sys_tablet_profile), s(R.string.rb_sys_tablet_profile_sub), ui.chevron()) {
            ui.ask(s(R.string.rb_sys_tablet_profile), s(R.string.rb_sys_tablet_profile_ask), s(R.string.rb_sys_apply)) {
                bg({ TabletProfile.apply(context) })
            }
        }.view)
    }

    private fun gb(bytes: Long) = String.format("%.1f GB", bytes / 1_073_741_824.0)
}

class PowerPanel(ui: RbUi) : Panel(ui) {
    override fun render() {
        root.removeAllViews()
        root.addView(ui.row(s(R.string.rb_sys_reboot), s(R.string.rb_sys_reboot_sub), ui.chevron()) {
            ui.ask(s(R.string.rb_sys_reboot), null, s(R.string.rb_sys_reboot)) { bg({ SystemAccess.reboot(context) }, {}) }
        }.view)
        root.addView(ui.row(s(R.string.rb_sys_power_off), null, ui.chevron()) {
            ui.ask(s(R.string.rb_sys_power_off), null, s(R.string.rb_sys_power_off)) { bg({ SystemAccess.powerOff(context) }, {}) }
        }.view)
    }
}
