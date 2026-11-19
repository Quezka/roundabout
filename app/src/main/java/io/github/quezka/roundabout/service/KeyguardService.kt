package io.github.quezka.roundabout.service

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

// keeps lock screen off while process alive ; works only if lock type != pin|pattern|password
class KeyguardService : Service() {
    @Suppress("DEPRECATION")
    private var lock: KeyguardManager.KeyguardLock? = null

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        lock = km.newKeyguardLock("roundabout").also { it.disableKeyguard() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        lock?.reenableKeyguard()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                context.startService(Intent(context, KeyguardService::class.java))
            } catch (e: Exception) {
                // api 26+ background start limit ; target tablet = api 22
            }
        }
    }
}
