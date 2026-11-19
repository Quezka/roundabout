package io.github.quezka.roundabout.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.github.quezka.roundabout.domain.PowerSource
import io.github.quezka.roundabout.domain.PowerState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BatteryPowerSource(private val context: Context) : PowerSource {

    override fun state(): Flow<PowerState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { read(i)?.let { trySend(it) } }
        }
        // sticky -> ret = current state
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i -> read(i)?.let { trySend(it) } }
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun read(i: Intent): PowerState? {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return PowerState(level * 100 / scale, plugged)
    }
}
