package io.github.quezka.roundabout.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

// the tablet exists to run roundabout ; anything else that lingers in the
// background is dead weight on 1gb of ram. every 15 min we ask init to kill
// background processes (foreground + our own service survive).
object Janitor {
    const val INTERVAL = 15 * 60 * 1000L
    private const val REQ = 4211

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx,
            REQ,
            Intent(ctx, JanitorReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL,
            INTERVAL,
            pi,
        )
    }

    fun runOnce() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "am kill-all")).waitFor()
        } catch (e: Exception) {
            // no root, nothing to do
        }
    }
}

class JanitorReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Janitor.runOnce()
    }
}
