package com.andrerinas.openheadunit.roundabout.system

import android.content.Context
import android.provider.Settings
import com.andrerinas.openheadunit.utils.Settings as Oh

// best cfg for prestigio `PMT3797` (atom x3 sofia, 1.4 gb, 1280x800 ~7", 5.1) as car headunit
// run @ setup done + "apply tablet profile" ; ! off main thread
object TabletProfile {
    const val HOME_ZONE = "Europe/Rome"

    fun apply(context: Context) {
        val saved = loadSaved(context)
        if (saved != null) {
            applySaved(context, saved)
            return
        }
        applyApp(context)
        applySystem(context)
    }

    // --- saved profile = current tablet state, saved from settings -> tablet -> about ---
    // where: app files (survives flash, data kept) ; rom: `/system/etc/roundabout/profile.json` (build.py --profile)
    private const val FILE = "tablet-profile.json"
    private const val ROM_FILE = "/system/etc/roundabout/profile.json"

    // no device / phone / wifi direct ids, caches, migration flags, setup state, primary-connection
    private val DENY = Regex("^(wifi-direct-(?!band)|soft-ap|last-|cached-|boot-loop|pending|station|native-aa-wake|" +
        "native-aa-radio|native-ap-transport|wifi-restart|wireless-paused|home-widget|headunit-server)|mac|address|" +
        "verdict|hint|digest|ssid|psk|passphrase|-v\\d+$|^rb-overscan|^rb-setup-done|^has-|^onboarding|" +
        "^primary-connection|^auto-start-bt-name|^vehicle-id$|^home-favorite|-ip$|latitude|longitude|" +
        "^settings-rail|^allow-devices")

    // ns:key ; ns = global | system | secure
    private val SYSTEM_KEYS = listOf(
        "global:stay_on_while_plugged_in", "global:wifi_sleep_policy", "global:auto_time", "global:auto_time_zone",
        "global:animator_duration_scale", "global:transition_animation_scale", "global:window_animation_scale",
        "global:heads_up_notifications_enabled", "global:bluetooth_on",
        "system:screen_off_timeout", "system:time_12_24", "system:accelerometer_rotation", "system:user_rotation",
        "system:sound_effects_enabled", "system:haptic_feedback_enabled", "system:lockscreen_sounds_enabled",
        "system:screen_brightness", "system:screen_brightness_mode",
        "secure:location_mode", "secure:lockscreen.disabled",
    )

    fun hasSaved(context: Context): Boolean = java.io.File(context.filesDir, FILE).exists()

    fun save(context: Context): Int {
        val json = org.json.JSONObject()
        val app = org.json.JSONObject()
        context.getSharedPreferences(Oh.PREFS_NAME, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (DENY.containsMatchIn(k) || v == null) return@forEach
            app.put(k, when (v) {
                is Boolean -> org.json.JSONObject().put("b", v)
                is Int -> org.json.JSONObject().put("i", v)
                is Long -> org.json.JSONObject().put("l", v)
                is Float -> org.json.JSONObject().put("f", v.toDouble())
                is String -> org.json.JSONObject().put("s", v)
                is Set<*> -> org.json.JSONObject().put("set", org.json.JSONArray(v.map { it.toString() }))
                else -> return@forEach
            })
        }
        val sys = org.json.JSONObject()
        val cr = context.contentResolver
        SYSTEM_KEYS.forEach { nk ->
            val (ns, k) = nk.split(":", limit = 2)
            val v = when (ns) {
                "global" -> Settings.Global.getString(cr, k)
                "secure" -> Settings.Secure.getString(cr, k)
                else -> Settings.System.getString(cr, k)
            } ?: return@forEach
            sys.put(nk, v)
        }
        json.put("app", app).put("system", sys).put("zone", java.util.TimeZone.getDefault().id)
        java.io.File(context.filesDir, FILE).writeText(json.toString(1))
        return app.length() + sys.length()
    }

    private fun loadSaved(context: Context): org.json.JSONObject? {
        val own = java.io.File(context.filesDir, FILE)
        val f = if (own.exists()) own else java.io.File(ROM_FILE).takeIf { it.canRead() } ?: return null
        return try { org.json.JSONObject(f.readText()) } catch (e: Exception) { null }
    }

    private fun applySaved(c: Context, json: org.json.JSONObject) {
        val e = c.getSharedPreferences(Oh.PREFS_NAME, Context.MODE_PRIVATE).edit()
        val app = json.optJSONObject("app") ?: org.json.JSONObject()
        app.keys().forEach { k ->
            val v = app.getJSONObject(k)
            when {
                v.has("b") -> e.putBoolean(k, v.getBoolean("b"))
                v.has("i") -> e.putInt(k, v.getInt("i"))
                v.has("l") -> e.putLong(k, v.getLong("l"))
                v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat())
                v.has("s") -> e.putString(k, v.getString("s"))
                v.has("set") -> e.putStringSet(k, v.getJSONArray("set").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            }
        }
        e.commit()
        val sys = json.optJSONObject("system") ?: org.json.JSONObject()
        sys.keys().forEach { nk ->
            val (ns, k) = nk.split(":", limit = 2)
            val v = sys.getString(nk)
            when (ns) {
                "global" -> SystemAccess.putGlobal(c, k, v)
                "secure" -> SystemAccess.putSecure(c, k, v)
                else -> SystemAccess.putSystem(c, k, v)
            }
        }
        json.optString("zone").takeIf { it.isNotEmpty() && it != java.util.TimeZone.getDefault().id }
            ?.let { SystemAccess.setTimeZone(c, it) }
        // never saved: provisioning + roundabout access
        SystemAccess.putGlobal(c, Settings.Global.DEVICE_PROVISIONED, "1")
        SystemAccess.putSecure(c, "user_setup_complete", "1")
        SystemAccess.grantAll(c)
    }

    // app settings = snapshot of the tuned tablet (TabletProfileSnapshot) ; raw prefs <- exact values
    fun applyApp(context: Context) {
        val e = context.getSharedPreferences(Oh.PREFS_NAME, Context.MODE_PRIVATE).edit()
        TabletProfileSnapshot.values.forEach { v ->
            when (v) {
                is TabletProfileSnapshot.B -> e.putBoolean(v.key, v.v)
                is TabletProfileSnapshot.I -> e.putInt(v.key, v.v)
                is TabletProfileSnapshot.L -> e.putLong(v.key, v.v)
                is TabletProfileSnapshot.S -> e.putString(v.key, v.v)
                is TabletProfileSnapshot.Set -> e.putStringSet(v.key, v.v)
            }
        }
        e.commit()
    }

    private fun applySystem(c: Context) {
        val sys = SystemAccess
        // awake while powered (ac + usb + wireless = 7) ; battery -> off after 5 min
        sys.putGlobal(c, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
        sys.putSystem(c, Settings.System.SCREEN_OFF_TIMEOUT, "300000")
        // wifi never sleeps <- wireless aa + hotspot
        sys.putGlobal(c, Settings.Global.WIFI_SLEEP_POLICY, Settings.Global.WIFI_SLEEP_POLICY_NEVER.toString())
        // weak gpu -> half animations
        DisplayPanel.animations(c, "0.5")
        // network time + zone
        sys.putGlobal(c, Settings.Global.AUTO_TIME, "1")
        // zone: auto (as on the tuned tablet) ; home zone first <- no sim => auto may never fire
        if (java.util.TimeZone.getDefault().id != HOME_ZONE) sys.setTimeZone(c, HOME_ZONE)
        sys.putGlobal(c, Settings.Global.AUTO_TIME_ZONE, "1")
        sys.putSystem(c, Settings.System.TIME_12_24, "12")
        // landscape lock
        sys.putSystem(c, Settings.System.ACCELEROMETER_ROTATION, "0")
        sys.putSystem(c, Settings.System.USER_ROTATION, "1")
        // quiet car: no touch clicks, no lock sounds, no haptics
        sys.putSystem(c, Settings.System.SOUND_EFFECTS_ENABLED, "0")
        sys.putSystem(c, Settings.System.HAPTIC_FEEDBACK_ENABLED, "0")
        sys.putSystem(c, "lockscreen_sounds_enabled", "0")
        // no lock screen ; no setup wizard after flash
        sys.putSecure(c, "lockscreen.disabled", "1")
        sys.putGlobal(c, Settings.Global.DEVICE_PROVISIONED, "1")
        sys.putSecure(c, "user_setup_complete", "1")
        // updates of roundabout apk by hand
        sys.putGlobal(c, "install_non_market_apps", "1")
        sys.putGlobal(c, "package_verifier_enable", "0")
        // gps for oh location + speed dial
        sys.putSecure(c, "location_mode", "3")
        // no heads-up pops over aa
        sys.putGlobal(c, "heads_up_notifications_enabled", "0")
        // bluetooth on <- phone link
        sys.putGlobal(c, Settings.Global.BLUETOOTH_ON, "1")
        try { android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.enable() } catch (e: Exception) { }
        sys.grantAll(c)
    }
}
