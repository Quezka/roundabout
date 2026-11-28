package com.andrerinas.openheadunit.roundabout.system

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.andrerinas.openheadunit.R

// pairing code ui <- stock settings app (owner of this dialog) = rm @ rom
// dev install + stock settings present -> stock dialog shows ; skip ours => no double
class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        // pair screen open -> answers itself
        if (PairPhoneActivity.current?.get()?.onPairingRequest(intent) == true) return
        if (stockSettingsPresent(context) && !SystemAccess.privileged(context)) return
        context.startActivity(Intent(context, PairingActivity::class.java)
            .putExtras(intent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    companion object {
        const val ACTION = "android.bluetooth.device.action.PAIRING_REQUEST"

        fun stockSettingsPresent(context: Context): Boolean = try {
            context.packageManager.getApplicationInfo("com.android.settings", 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

@SuppressLint("MissingPermission")
class PairingActivity : Activity() {

    private var device: BluetoothDevice? = null
    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 19) { finish(); return }
        @Suppress("DEPRECATION")
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val d = device ?: return finish()
        val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION)
        val key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
        val ui = RbUi(this)

        val card = ui.vertical().apply {
            background = ui.rounded(ui.color(R.color.rb_card), 28f)
            setPadding(ui.dp(32f), ui.dp(28f), ui.dp(32f), ui.dp(28f))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        card.addView(ui.text(getString(R.string.rb_sys_bt_pair_title), 26f, strong = true))
        card.addView(ui.text(d.name ?: d.address, 20f, muted = true).apply { setPadding(0, ui.dp(6f), 0, ui.dp(18f)) })
        if (key >= 0 && variant != PIN) {
            card.addView(ui.text(String.format("%06d", key), 56f, strong = true).apply {
                setTextColor(ui.color(R.color.rb_accent))
                letterSpacing = 0.12f
            })
            card.addView(ui.note(getString(R.string.rb_sys_bt_pair_match)))
        } else {
            card.addView(ui.note(getString(R.string.rb_sys_bt_pair_confirm)))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(24f), 0, 0)
        }
        buttons.addView(ui.button(getString(R.string.cancel), primary = false) { answer(false, variant) })
        buttons.addView(ui.spacer(1f).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(16f), 1) })
        buttons.addView(ui.button(getString(R.string.rb_sys_bt_pair)) { answer(true, variant) })
        card.addView(buttons)

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.argb(0xCC, 0, 0, 0))
            addView(card, FrameLayout.LayoutParams(ui.dp(560f), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        })
    }

    private fun answer(yes: Boolean, variant: Int) {
        answered = true
        val d = device ?: return finish()
        try {
            if (yes && variant == PIN) d.setPin("0000".toByteArray())
            else d.setPairingConfirmation(yes)
        } catch (e: SecurityException) {
            // no BLUETOOTH_PRIVILEGED (dev install) -> pairing times out on its own
        }
        finish()
    }

    override fun onDestroy() {
        if (!answered && isFinishing) try { device?.setPairingConfirmation(false) } catch (e: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val PIN = 0 // BluetoothDevice.PAIRING_VARIANT_PIN
    }
}
