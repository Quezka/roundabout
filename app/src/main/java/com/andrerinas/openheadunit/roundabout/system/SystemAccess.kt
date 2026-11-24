package com.andrerinas.openheadunit.roundabout.system

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.andrerinas.openheadunit.app.ScreenOffAdminReceiver
import com.andrerinas.openheadunit.roundabout.MediaListener
import com.andrerinas.openheadunit.roundabout.NavAccessibility

// privileged writes for the tablet settings
// rom: roundabout = priv-app => api call works ; plain install => su fallback
// ! call off main thread <- su blocks
object SystemAccess {

    fun su(cmd: String): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0
    } catch (e: Exception) {
        false
    }

    fun putGlobal(context: Context, key: String, value: String): Boolean = try {
        Settings.Global.putString(context.contentResolver, key, value)
    } catch (e: SecurityException) {
        su("settings put global $key $value")
    }

    fun putSecure(context: Context, key: String, value: String): Boolean = try {
        Settings.Secure.putString(context.contentResolver, key, value)
    } catch (e: SecurityException) {
        su("settings put secure $key $value")
    }

    fun putSystem(context: Context, key: String, value: String): Boolean = try {
        Settings.System.putString(context.contentResolver, key, value)
    } catch (e: SecurityException) {
        su("settings put system $key $value")
    }

    fun setTimeZone(context: Context, id: String): Boolean = try {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setTimeZone(id)
        true
    } catch (e: SecurityException) {
        // root uid may send the protected broadcast ; shell uid may not
        su("setprop persist.sys.timezone $id && am broadcast -a android.intent.action.TIMEZONE_CHANGED --es time-zone $id")
    }

    fun setTime(context: Context, millis: Long): Boolean = try {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setTime(millis)
        true
    } catch (e: SecurityException) {
        su("date @${millis / 1000}")
    }

    fun reboot(context: Context) {
        try {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).reboot(null)
        } catch (e: SecurityException) {
            su("svc power reboot || reboot")
        }
    }

    fun powerOff() {
        su("svc power shutdown || reboot -p")
    }

    fun forceStop(pkg: String): Boolean = su("am force-stop $pkg")

    fun uninstall(pkg: String): Boolean = su("pm uninstall $pkg")

    fun clearData(pkg: String): Boolean = su("pm clear $pkg")

    fun setEnabled(pkg: String, on: Boolean): Boolean = su("pm ${if (on) "enable" else "disable"} $pkg")

    // everything roundabout needs from android: listener, accessibility, admin, secure settings
    fun grantAll(context: Context): Boolean {
        val pkg = context.packageName
        val listener = ComponentName(context, MediaListener::class.java).flattenToString()
        val access = ComponentName(context, NavAccessibility::class.java).flattenToString()
        val admin = ComponentName(context, ScreenOffAdminReceiver::class.java).flattenToString()
        return su(listOf(
            "settings put secure enabled_notification_listeners $listener",
            "settings put secure enabled_accessibility_services $access",
            "settings put secure accessibility_enabled 1",
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "dpm set-active-admin $admin",
            "true",
        ).joinToString(" ; "))
    }

    fun listenerOn(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(context, MediaListener::class.java).flattenToString()) == true

    fun accessibilityOn(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(ComponentName(context, NavAccessibility::class.java).flattenToString()) == true

    fun secureSettingsOn(context: Context): Boolean =
        context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
