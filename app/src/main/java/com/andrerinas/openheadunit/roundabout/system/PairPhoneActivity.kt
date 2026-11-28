package com.andrerinas.openheadunit.roundabout.system

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.main.MainActivity
import com.andrerinas.openheadunit.roundabout.RoundaboutBus
import com.andrerinas.openheadunit.utils.Settings
import java.lang.ref.WeakReference

// one-button pair @ home drive card: bt on + visible -> phone pairs -> lists + aa start
// tablet side confirms by itself while open <- user pressed button ; phone still confirms code
@SuppressLint("MissingPermission")
class PairPhoneActivity : Activity() {

    private enum class Step { WAITING, CODE, DONE, FAILED }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var ui: RbUi
    private lateinit var card: LinearLayout
    private var events: BroadcastReceiver? = null
    private var step = Step.WAITING
    private var phone: BluetoothDevice? = null
    private var code: Int = -1

    // discoverable max 300 s ; renew before it lapses
    private val keepVisible = object : Runnable {
        override fun run() {
            setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, VISIBLE_S)
            main.postDelayed(this, (VISIBLE_S - 20) * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 19) { finish(); return }
        ui = RbUi(this)
        card = ui.vertical().apply {
            background = ui.rounded(ui.color(R.color.rb_card), 28f)
            setPadding(ui.dp(36f), ui.dp(30f), ui.dp(36f), ui.dp(28f))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.argb(0xE6, 0, 0, 0))
            addView(card, FrameLayout.LayoutParams(ui.dp(640f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        })
        current = WeakReference(this)
        // bond events @ create..destroy <- fallback confirm dialog may cover screen
        events = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onEvent(i)
        }.also {
            registerReceiver(it, IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            })
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        val a = adapter
        if (a != null && !a.isEnabled) a.enable() else startVisible()
    }

    override fun onStop() {
        main.removeCallbacks(keepVisible)
        // hidden again once screen leaves
        setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE, 0)
        super.onStop()
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        try { events?.let { unregisterReceiver(it) } } catch (e: Exception) { }
        events = null
        super.onDestroy()
    }

    private fun startVisible() {
        main.removeCallbacks(keepVisible)
        if (step == Step.WAITING || step == Step.FAILED) keepVisible.run()
    }

    private fun onEvent(i: Intent) {
        when (i.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED ->
                if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0) == BluetoothAdapter.STATE_ON) { startVisible(); render() }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                @Suppress("DEPRECATION")
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (phone != null && phone?.address != d.address) return
                when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                    BluetoothDevice.BOND_BONDING -> { phone = d; if (step != Step.CODE) { step = Step.CODE; render() } }
                    BluetoothDevice.BOND_BONDED -> onPaired(d)
                    BluetoothDevice.BOND_NONE -> if (step == Step.CODE) { step = Step.FAILED; phone = null; code = -1; render() }
                }
            }
        }
    }

    // from PairingReceiver ; true = answered here => no PairingActivity
    fun onPairingRequest(intent: Intent): Boolean {
        @Suppress("DEPRECATION")
        val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return false
        if (step == Step.DONE) return false
        val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION)
        val ok = try {
            if (variant == PIN) d.setPin("0000".toByteArray()) else d.setPairingConfirmation(true)
        } catch (e: SecurityException) {
            // no BLUETOOTH_PRIVILEGED (dev install) -> normal confirm dialog
            false
        }
        if (!ok) return false
        phone = d
        code = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1).takeIf { variant != PIN } ?: -1
        step = Step.CODE
        main.post { render() }
        return true
    }

    private fun onPaired(d: BluetoothDevice) {
        phone = d
        step = Step.DONE
        main.removeCallbacks(keepVisible)
        setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE, 0)
        val s = Settings(this)
        s.nativePokeBtMacs = NewPhonePolicy.addTo(s.nativePokeBtMacs, d.address)
        s.autoStartBluetoothDeviceMacs = NewPhonePolicy.addTo(s.autoStartBluetoothDeviceMacs, d.address)
        render()
    }

    private fun name(d: BluetoothDevice?) = d?.name?.takeIf { it.isNotBlank() } ?: d?.address ?: ""

    private fun render() {
        card.removeAllViews()
        val a = adapter
        when {
            a == null -> {
                card.addView(ui.text(getString(R.string.rb_pair_title), 28f, strong = true))
                card.addView(ui.note(getString(R.string.rb_sys_bt_none)).apply { gravity = Gravity.CENTER })
                buttons(ui.button(getString(R.string.rb_pair_close)) { finish() })
            }
            step == Step.DONE -> {
                card.addView(ui.text(getString(R.string.rb_pair_done_title), 28f, strong = true))
                card.addView(big(name(phone)))
                card.addView(ui.note(getString(R.string.rb_pair_done_hint)).apply { gravity = Gravity.CENTER })
                buttons(
                    ui.button(getString(R.string.rb_pair_close), primary = false) { finish() },
                    ui.button(getString(R.string.rb_pair_connect)) { connect() },
                )
            }
            step == Step.CODE -> {
                card.addView(ui.text(getString(R.string.rb_pair_pairing), 28f, strong = true))
                card.addView(ui.text(name(phone), 20f, muted = true).apply { setPadding(0, ui.dp(6f), 0, ui.dp(10f)) })
                if (code >= 0) {
                    card.addView(big(String.format("%06d", code)).apply { letterSpacing = 0.12f })
                    card.addView(ui.note(getString(R.string.rb_pair_code_hint)).apply { gravity = Gravity.CENTER })
                } else {
                    card.addView(ui.note(getString(R.string.rb_pair_accept_hint)).apply { gravity = Gravity.CENTER })
                }
                buttons(ui.button(getString(R.string.cancel), primary = false) { finish() })
            }
            else -> {
                card.addView(ui.text(getString(if (step == Step.FAILED) R.string.rb_pair_failed else R.string.rb_pair_title), 28f, strong = true))
                if (!a.isEnabled) {
                    card.addView(ui.note(getString(R.string.rb_pair_turning_on)).apply { gravity = Gravity.CENTER })
                } else {
                    card.addView(ui.note(getString(R.string.rb_pair_step_open)).apply { gravity = Gravity.CENTER })
                    card.addView(big(a.name ?: ""))
                    card.addView(ui.note(getString(R.string.rb_pair_step_confirm)).apply { gravity = Gravity.CENTER })
                }
                buttons(ui.button(getString(R.string.cancel), primary = false) { finish() })
            }
        }
    }

    private fun big(str: String) = ui.text(str, 44f, strong = true).apply {
        setTextColor(ui.color(R.color.rb_accent))
        gravity = Gravity.CENTER
        setPadding(0, ui.dp(8f), 0, ui.dp(8f))
    }

    private fun buttons(vararg views: android.view.View) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(24f), 0, 0)
        }
        views.forEachIndexed { n, v ->
            if (n > 0) row.addView(android.view.View(this), LinearLayout.LayoutParams(ui.dp(16f), 1))
            row.addView(v)
        }
        card.addView(row)
    }

    private fun connect() {
        RoundaboutBus.startAndroidAuto = true
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    // hidden setScanMode ; priv-app only => silent no-op @ dev install
    private fun setScanMode(mode: Int, seconds: Int) {
        try {
            val a = adapter ?: return
            if (!a.isEnabled) return
            a.javaClass.getMethod("setScanMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(a, mode, seconds)
        } catch (e: Exception) { }
    }

    companion object {
        private const val PIN = 0 // BluetoothDevice.PAIRING_VARIANT_PIN
        private const val VISIBLE_S = 300
        @Volatile var current: WeakReference<PairPhoneActivity>? = null

        fun open(context: Context) {
            context.startActivity(Intent(context, PairPhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

// new phone -> wake poke + auto-start lists
// empty list = "all paired" | nothing chosen => keep empty ; else add, no dup (mac case differs)
object NewPhonePolicy {
    fun addTo(list: Set<String>, mac: String): Set<String> =
        if (list.isEmpty() || list.any { it.equals(mac, ignoreCase = true) }) list else list + mac.uppercase()
}
