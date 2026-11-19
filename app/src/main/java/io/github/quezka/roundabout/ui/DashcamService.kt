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
    private var current: Lens? = null
    private var cabinWorks = true
    private var currentName: String? = null
    private var lastName: String? = null
    private var saveCurrent = false
    private var lastEvent = 0L
    private val sensors by lazy { getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager }
    private val impact = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            if (io.github.quezka.roundabout.domain.ImpactDetector.isImpact(e.values[0], e.values[1], e.values[2])) markEvent()
        }
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("dashcam").apply { start() }
        // own looper -> recorder callbacks off main thread
        handler = Handler(thread.looper)
        startForeground(NOTIFICATION_ID, notification())
        handler.post {
            state.value = true
            next()
        }
        // ~5 hz ; only while recording
        sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(impact, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
        instance = this
    }

    // keep prev clip now + current clip when it closes
    fun markEvent() {
        handler.post {
            val now = System.currentTimeMillis()
            if (now - lastEvent < io.github.quezka.roundabout.domain.ImpactDetector.COOLDOWN_MS) return@post
            lastEvent = now
            val store = AppGraph.get(this).clips
            lastName?.let { n -> store.clips().firstOrNull { it.name == n }?.let(store::protect) }
            saveCurrent = true
            events.tryEmit(now)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    // one cam open at a time ; road -> cabin -> road ...
    private fun next() {
        current?.let { recorders[it]?.close() }
        recorders.clear()
        val finished = currentName
        if (finished != null) {
            if (saveCurrent) {
                val store = AppGraph.get(this).clips
                store.clips().firstOrNull { it.name == finished }?.let(store::protect)
                saveCurrent = false
            }
            lastName = finished
        }
        prune()
        var lens = DashcamPolicy.nextLens(current, cabinWorks)
        var r = AppGraph.get(this).recorder(lens)
        if (!r.open()) {
            if (lens == Lens.CABIN) cabinWorks = false
            lens = Lens.ROAD
            r = AppGraph.get(this).recorder(lens)
            if (!r.open()) { stopSelf(); return }
        }
        val c = Calendar.getInstance()
        val name = DashcamPolicy.clipName(lens,
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
        current = lens
        currentName = name
        recorders[lens] = r
        lenses.value = setOf(lens)
        if (!r.start(name, DashcamPolicy.durationMs(lens)) { handler.post { next() } }) {
            r.close()
            recorders.clear()
            if (lens == Lens.CABIN) { cabinWorks = false; handler.post { next() } } else stopSelf()
        }
    }

    private fun prune() {
        val store = AppGraph.get(this).clips
        DashcamPolicy.toDelete(store.clips(), store.freeBytes()).forEach(store::delete)
    }

    override fun onDestroy() {
        instance = null
        sensors.unregisterListener(impact)
        // stop -> close segment first ; then save if event pending
        handler.post {
            recorders.values.forEach { it.close() }
            recorders.clear()
            if (saveCurrent) currentName?.let { n ->
                val store = AppGraph.get(this).clips
                store.clips().firstOrNull { it.name == n }?.let(store::protect)
            }
        }
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
        @Volatile private var instance: DashcamService? = null
        val events = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 4)

        // manual save ; false if not recording
        fun saveEvent(): Boolean = instance?.let { it.markEvent(); true } ?: false
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
