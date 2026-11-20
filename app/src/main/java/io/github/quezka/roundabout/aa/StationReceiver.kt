package io.github.quezka.roundabout.aa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// `adb shell am broadcast -a io.github.quezka.roundabout.STATION` drives the
// network-sharing-free test path without wearing out the touchscreen
class StationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AaClient(ctx.applicationContext).connectStation()
    }
}
