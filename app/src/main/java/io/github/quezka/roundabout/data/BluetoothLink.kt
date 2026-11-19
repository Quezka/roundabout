package io.github.quezka.roundabout.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// bt radio on/off ; phone link needs it <- a2dp + AA wireless
class BluetoothLink(private val context: Context) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun setEnabled(on: Boolean): Boolean {
        val a = adapter ?: return false
        return try {
            if (on) a.enable() else a.disable()
        } catch (e: SecurityException) {
            false
        }
    }

    fun state(): Flow<Boolean> = callbackFlow {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { trySend(isEnabled()) }
        }
        context.registerReceiver(r, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        trySend(isEnabled())
        awaitClose { try { context.unregisterReceiver(r) } catch (e: Exception) { } }
    }
}
