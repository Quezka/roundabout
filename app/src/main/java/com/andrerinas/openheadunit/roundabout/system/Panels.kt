// tablet only (5.1) ; activities stop < api 21
@file:android.annotation.SuppressLint("NewApi")

package com.andrerinas.openheadunit.roundabout.system

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.LinearLayout
import com.andrerinas.openheadunit.R
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// one settings screen part ; reused by tablet settings + setup
abstract class Panel(protected val ui: RbUi) {
    protected val context: Context get() = ui.context
    protected val main = Handler(Looper.getMainLooper())
    protected lateinit var root: LinearLayout

    fun attach(into: LinearLayout) {
        root = into
        start()
        render()
    }

    abstract fun render()
    open fun start() {}
    open fun stop() {}

    protected fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    protected fun bg(work: () -> Unit, then: () -> Unit = { render() }) {
        Thread { work(); main.post { if (::root.isInitialized) then() } }.start()
    }

    protected fun receiver(filter: IntentFilter, onEvent: (Intent) -> Unit): BroadcastReceiver {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onEvent(i)
        }
        context.registerReceiver(r, filter)
        return r
    }

    protected fun unregister(r: BroadcastReceiver?) {
        try { r?.let { context.unregisterReceiver(it) } } catch (e: Exception) { }
    }
}

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
class WifiPanel(ui: RbUi) : Panel(ui) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var events: BroadcastReceiver? = null
    private val rescan = object : Runnable {
        override fun run() {
            if (wifi.isWifiEnabled) wifi.startScan()
            main.postDelayed(this, 12_000)
        }
    }

    override fun start() {
        events = receiver(IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        }) { render() }
        main.post(rescan)
    }

    override fun stop() {
        main.removeCallbacks(rescan)
        unregister(events)
    }

    private fun quoted(ssid: String) = "\"$ssid\""
    private fun bare(ssid: String?) = ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""

    private fun security(r: ScanResult): String = when {
        r.capabilities.contains("WPA") -> "WPA"
        r.capabilities.contains("WEP") -> "WEP"
        else -> ""
    }

    override fun render() {
        root.removeAllViews()
        root.addView(ui.toggle(s(R.string.rb_sys_wifi), null, wifi.isWifiEnabled) { on ->
            wifi.isWifiEnabled = on
        })
        if (!wifi.isWifiEnabled) {
            root.addView(ui.note(s(R.string.rb_sys_wifi_off)))
            return
        }
        val info = wifi.connectionInfo
        val current = bare(info?.ssid).takeIf { info != null && info.networkId != -1 }
        val saved = (wifi.configuredNetworks ?: emptyList()).associateBy { bare(it.SSID) }

        if (current != null) {
            root.addView(ui.header(s(R.string.rb_sys_connected)))
            val ip = info.ipAddress
            val ipStr = if (ip == 0) s(R.string.rb_sys_getting_ip)
                else "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            root.addView(ui.row(current, ipStr, ui.value(bars(info.rssi))) {
                saved[current]?.let { cfg -> forget(cfg) }
            }.view)
        }

        root.addView(ui.header(s(R.string.rb_sys_networks)))
        val results = (wifi.scanResults ?: emptyList())
            .filter { it.SSID.isNotEmpty() && it.SSID != current }
            .groupBy { it.SSID }.map { (_, list) -> list.maxByOrNull { it.level }!! }
            .sortedByDescending { it.level }
        if (results.isEmpty()) root.addView(ui.note(s(R.string.rb_sys_searching)))
        results.forEach { r ->
            val sec = security(r)
            val known = saved[r.SSID]
            val sub = when {
                known != null -> s(R.string.rb_sys_saved)
                sec.isEmpty() -> s(R.string.rb_sys_open)
                else -> s(R.string.rb_sys_secured, sec)
            }
            val row = ui.row(r.SSID, sub, ui.value(bars(r.level))) {
                when {
                    known != null -> connect(known.networkId)
                    sec.isEmpty() -> add(r.SSID, sec, null)
                    else -> ui.password(r.SSID) { pw -> add(r.SSID, sec, pw) }
                }
            }
            if (known != null) row.view.setOnLongClickListener { forget(known); true }
            root.addView(row.view)
        }
        if (saved.isNotEmpty()) root.addView(ui.note(s(R.string.rb_sys_wifi_forget_hint)))
    }

    private fun bars(level: Int): String = when (WifiManager.calculateSignalLevel(level, 4)) {
        3 -> "▂▄▆█"; 2 -> "▂▄▆"; 1 -> "▂▄"; else -> "▂"
    }

    private fun add(ssid: String, sec: String, password: String?) {
        val cfg = WifiConfiguration().apply {
            SSID = quoted(ssid)
            when (sec) {
                "WPA" -> preSharedKey = quoted(password ?: "")
                "WEP" -> {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                    wepKeys[0] = if (password != null && password.matches(Regex("[0-9A-Fa-f]{10}|[0-9A-Fa-f]{26}"))) password else quoted(password ?: "")
                    wepTxKeyIndex = 0
                }
                else -> allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
        }
        val id = wifi.addNetwork(cfg)
        if (id != -1) {
            wifi.saveConfiguration()
            connect(id)
        }
    }

    private fun connect(id: Int) {
        wifi.disconnect()
        wifi.enableNetwork(id, true)
        wifi.reconnect()
        render()
    }

    private fun forget(cfg: WifiConfiguration) {
        ui.ask(bare(cfg.SSID), null, s(R.string.rb_sys_forget)) {
            wifi.removeNetwork(cfg.networkId)
            wifi.saveConfiguration()
            render()
        }
    }
}

@SuppressLint("MissingPermission")
class BluetoothPanel(ui: RbUi) : Panel(ui) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val found = LinkedHashMap<String, BluetoothDevice>()
    private var events: BroadcastReceiver? = null

    override fun start() {
        events = receiver(IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
        }) { i ->
            if (i.action == BluetoothDevice.ACTION_FOUND) {
                @Suppress("DEPRECATION")
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // nameless = beacons, tags, headless gear -> noise in a phone list
                if (d != null && d.bondState != BluetoothDevice.BOND_BONDED && !d.name.isNullOrBlank()) found[d.address] = d
            }
            if (i.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                i.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0) == BluetoothAdapter.STATE_ON) adapter?.startDiscovery()
            render()
        }
        if (adapter?.isEnabled == true) adapter.startDiscovery()
        // visible while pairing screen open <- phone finds the tablet
        makeVisible()
    }

    override fun stop() {
        try { adapter?.cancelDiscovery() } catch (e: Exception) { }
        unregister(events)
    }

    // hidden setScanMode ; WRITE_SECURE_SETTINGS-level @ priv-app
    private fun makeVisible() {
        try {
            val a = adapter ?: return
            a.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(a, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 300)
        } catch (e: Exception) { }
    }

    private fun name(d: BluetoothDevice) = d.name?.takeIf { it.isNotBlank() } ?: d.address

    override fun render() {
        root.removeAllViews()
        val a = adapter
        if (a == null) {
            root.addView(ui.note(s(R.string.rb_sys_bt_none)))
            return
        }
        root.addView(ui.toggle(s(R.string.rb_sys_bt), s(R.string.rb_sys_bt_name, a.name ?: ""), a.isEnabled) { on ->
            if (on) a.enable() else a.disable()
        })
        if (!a.isEnabled) return

        val paired = a.bondedDevices ?: emptySet()
        if (paired.isNotEmpty()) root.addView(ui.header(s(R.string.rb_sys_bt_paired)))
        paired.sortedBy { name(it) }.forEach { d ->
            val row = ui.row(name(d), s(R.string.rb_sys_bt_paired_hint)) {
                ui.ask(name(d), null, s(R.string.rb_sys_bt_unpair)) { unpair(d) }
            }
            root.addView(row.view)
        }

        root.addView(ui.header(s(R.string.rb_sys_bt_nearby)))
        val nearby = found.values.filter { it.bondState != BluetoothDevice.BOND_BONDED }
        if (nearby.isEmpty()) root.addView(ui.note(s(if (a.isDiscovering) R.string.rb_sys_searching else R.string.rb_sys_bt_none_found)))
        nearby.forEach { d ->
            val sub = if (d.bondState == BluetoothDevice.BOND_BONDING) s(R.string.rb_sys_bt_pairing) else s(R.string.rb_sys_bt_tap_pair)
            root.addView(ui.row(name(d), sub) {
                a.cancelDiscovery()
                if (Build.VERSION.SDK_INT >= 19) d.createBond()
            }.view)
        }
        if (!a.isDiscovering) {
            root.addView(ui.spacer(8f))
            root.addView(ui.button(s(R.string.rb_sys_bt_search), primary = false) { found.clear(); a.startDiscovery(); makeVisible() })
        }
    }

    private fun unpair(d: BluetoothDevice) {
        try { d.javaClass.getMethod("removeBond").invoke(d) } catch (e: Exception) { }
        render()
    }
}

class TimePanel(ui: RbUi) : Panel(ui) {
    private var tick: BroadcastReceiver? = null

    override fun start() {
        tick = receiver(IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }) { render() }
    }

    override fun stop() = unregister(tick)

    private fun global(key: String, def: Int) = Settings.Global.getInt(context.contentResolver, key, def)

    override fun render() {
        root.removeAllViews()
        val now = Calendar.getInstance()
        val autoTime = global(Settings.Global.AUTO_TIME, 1) == 1
        val autoZone = global(Settings.Global.AUTO_TIME_ZONE, 1) == 1
        val h24 = DateFormat.is24HourFormat(context)

        root.addView(ui.title(DateFormat.getTimeFormat(context).format(now.time)))
        root.addView(ui.note(DateFormat.getLongDateFormat(context).format(now.time)))
        root.addView(ui.toggle(s(R.string.rb_sys_auto_time), s(R.string.rb_sys_auto_time_sub), autoTime) { on ->
            bg({ SystemAccess.putGlobal(context, Settings.Global.AUTO_TIME, if (on) "1" else "0") })
        })
        if (!autoTime) {
            root.addView(ui.row(s(R.string.rb_sys_date), DateFormat.getMediumDateFormat(context).format(now.time), ui.chevron()) {
                DatePickerDialog(context, { _, y, m, d ->
                    val c = Calendar.getInstance().apply { set(y, m, d) }
                    bg({ SystemAccess.setTime(context, c.timeInMillis) })
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
            }.view)
            root.addView(ui.row(s(R.string.rb_sys_time), DateFormat.getTimeFormat(context).format(now.time), ui.chevron()) {
                TimePickerDialog(context, { _, h, m ->
                    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
                    bg({ SystemAccess.setTime(context, c.timeInMillis) })
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), h24).show()
            }.view)
        }
        root.addView(ui.toggle(s(R.string.rb_sys_auto_zone), null, autoZone) { on ->
            bg({ SystemAccess.putGlobal(context, Settings.Global.AUTO_TIME_ZONE, if (on) "1" else "0") })
        })
        if (!autoZone) {
            val tz = TimeZone.getDefault()
            root.addView(ui.row(s(R.string.rb_sys_zone), zoneLabel(tz.id), ui.chevron()) { pickZone() }.view)
        }
        root.addView(ui.toggle(s(R.string.rb_sys_24h), null, h24) { on ->
            bg({ SystemAccess.putSystem(context, Settings.System.TIME_12_24, if (on) "24" else "12") })
        })
    }

    // region first, then city a-z <- one list of ~400 zones = no scroll to find
    private fun pickZone() {
        val byRegion = zones().groupBy { it.substringBefore('/') }
        val regions = byRegion.keys.sorted()
        ui.pick(s(R.string.rb_sys_zone), regions) { r ->
            val ids = byRegion.getValue(regions[r]).sortedBy { it.substringAfter('/') }
            ui.pick(regions[r], ids.map { zoneLabel(it) }) { i ->
                bg({ SystemAccess.setTimeZone(context, ids[i]) })
            }
        }
    }

    companion object {
        // region/city ids only ; sorted by offset then name
        fun zones(): List<String> = TimeZone.getAvailableIDs()
            .filter { Regex("^(Africa|America|Asia|Atlantic|Australia|Europe|Indian|Pacific)/[^/]+$").matches(it) }
            .sortedWith(compareBy({ TimeZone.getTimeZone(it).rawOffset }, { it }))

        fun zoneLabel(id: String): String {
            val tz = TimeZone.getTimeZone(id)
            val off = tz.getOffset(System.currentTimeMillis()) / 60_000
            val sign = if (off < 0) "-" else "+"
            val city = id.substringAfter('/').replace('_', ' ')
            return String.format(Locale.US, "%s  (GMT%s%02d:%02d)", city, sign, Math.abs(off) / 60, Math.abs(off) % 60)
        }
    }
}

class LanguagePanel(ui: RbUi, private val onPicked: () -> Unit = {}) : Panel(ui) {
    override fun render() {
        root.removeAllViews()
        val settings = com.andrerinas.openheadunit.utils.Settings(context)
        val current = settings.appLanguage.ifEmpty { Locale.getDefault().language }
        LANGUAGES.forEach { (tag, label) ->
            val on = current.equals(tag, true) || current.startsWith("$tag-", true)
            val row = ui.row(label, null, if (on) ui.value("✓") else null) {
                apply(context, tag)
                onPicked()
            }
            if (on) row.view.background = ui.rounded(ui.color(R.color.rb_accent_container))
            root.addView(row.view)
        }
    }

    companion object {
        // tags = app translations in res/values-*
        val LANGUAGES = listOf(
            "en" to "English", "it" to "Italiano", "de" to "Deutsch", "fr" to "Français",
            "es" to "Español", "pt-BR" to "Português (Brasil)", "pt-PT" to "Português", "nl" to "Nederlands",
            "pl" to "Polski", "cs" to "Čeština", "hu" to "Magyar", "ro" to "Română", "tr" to "Türkçe",
            "ru" to "Русский", "uk" to "Українська", "ka" to "ქართული", "ar" to "العربية", "hi" to "हिन्दी",
            "th" to "ไทย", "vi" to "Tiếng Việt", "in" to "Bahasa Indonesia", "ja" to "日本語", "ko" to "한국어",
            "zh-CN" to "简体中文", "zh-TW" to "繁體中文",
        )

        // app + system locale ; system = LocalePicker (CHANGE_CONFIGURATION @ priv-app) ; fail -> app only
        fun apply(context: Context, tag: String) {
            com.andrerinas.openheadunit.utils.Settings(context).appLanguage = tag
            val parts = tag.split('-')
            val locale = if (parts.size > 1) Locale(parts[0], parts[1]) else Locale(parts[0])
            try {
                Class.forName("com.android.internal.app.LocalePicker")
                    .getMethod("updateLocale", Locale::class.java).invoke(null, locale)
            } catch (e: Exception) { }
        }
    }
}
