package io.github.quezka.roundabout.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.di.AppGraph
import io.github.quezka.roundabout.domain.DashRecorder
import io.github.quezka.roundabout.domain.DashcamPolicy
import io.github.quezka.roundabout.domain.Lens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

// loop recorder ; foreground <- else killed @ low ram
class DashcamService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val recorders = mutableMapOf<Lens, DashRecorder>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("dashcam").apply { start() }
        // own looper -> recorder callbacks off main thread
        handler = Handler(thread.looper)
        startForeground(NOTIFICATION_ID, notification())
        handler.post {
            // each lens own loop ; cabin optional <- hw may refuse 2 cams
            Lens.entries.forEach { lens ->
                val r = AppGraph.get(this).recorder(lens)
                if (r.open()) recorders[lens] = r
            }
            lenses.value = recorders.keys.toSet()
            if (recorders.isEmpty()) {
                state.value = false
                stopSelf()
                return@post
            }
            state.value = true
            recorders.keys.toList().forEach { next(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    private fun next(lens: Lens) {
        val r = recorders[lens] ?: return
        r.stopSegment()
        prune()
        val c = Calendar.getInstance()
        val name = DashcamPolicy.clipName(lens,
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
        if (!r.start(name) { handler.post { next(lens) } }) {
            r.close()
            recorders.remove(lens)
            lenses.value = recorders.keys.toSet()
            if (recorders.isEmpty()) stopSelf()
        }
    }

    private fun prune() {
        val store = AppGraph.get(this).clips
        DashcamPolicy.toDelete(store.clips(), store.freeBytes()).forEach(store::delete)
    }

    override fun onDestroy() {
        handler.post {
            recorders.values.forEach { it.close() }
            recorders.clear()
            thread.quitSafely()
        }
        state.value = false
        lenses.value = emptySet()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val b = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.dashcam), NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        val stop = PendingIntent.getService(this, 1, Intent(this, DashcamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        return b.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.dashcam_recording))
            .setOngoing(true)
            .setContentIntent(stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "dashcam"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "stop"
        private val state = MutableStateFlow(false)
        val recording: StateFlow<Boolean> get() = state
        // lenses actually recording
        val lenses = MutableStateFlow<Set<Lens>>(emptySet())

        fun start(context: Context) {
            try { context.startService(Intent(context, DashcamService::class.java)) } catch (e: Exception) { }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DashcamService::class.java))
        }

        fun toggle(context: Context) = if (state.value) stop(context) else start(context)
    }
}
