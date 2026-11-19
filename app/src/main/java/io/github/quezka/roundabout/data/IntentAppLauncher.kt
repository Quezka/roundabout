package io.github.quezka.roundabout.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.AppLauncher

class IntentAppLauncher(private val context: Context) : AppLauncher {

    override fun launch(app: AppEntry) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        start(i) || start(context.packageManager.getLaunchIntentForPackage(app.packageName))
    }

    override fun openSystemSettings() {
        start(Intent(Settings.ACTION_SETTINGS))
    }

    override fun openNotificationAccess() {
        // const = api 22 ; literal works @ api 21
        start(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    override fun openAppInfo(packageName: String) {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun start(intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
