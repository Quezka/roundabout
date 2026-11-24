package com.andrerinas.openheadunit.roundabout.system

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import com.andrerinas.openheadunit.app.ScreenOffAdminReceiver
import com.andrerinas.openheadunit.roundabout.MediaListener
import com.andrerinas.openheadunit.roundabout.NavAccessibility

// privileged writes for roundabout + tablet settings ; no root need
// rom: roundabout = `/system/priv-app` => signature|system perms granted -> api call ok
// hidden api via reflection = ok @ 5.1 (no hidden api block < 9)
// su = last fallback only <- dev install on rooted tablet != priv-app
// ! call off main thread <- su fallback blocks
object SystemAccess {

    fun su(cmd: String): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0
    } catch (e: Exception) {
        false
    }

    private inline fun orSu(cmd: String, block: () -> Boolean): Boolean = try {
        block() || su(cmd)
    } catch (e: SecurityException) {
        su(cmd)
    } catch (e: ReflectiveOperationException) {
        su(cmd)
    } catch (e: RuntimeException) {
        su(cmd)
    }

    fun privileged(context: Context): Boolean =
        context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED

    fun putGlobal(context: Context, key: String, value: String): Boolean =
        orSu("settings put global $key $value") { Settings.Global.putString(context.contentResolver, key, value) }

    fun putSecure(context: Context, key: String, value: String): Boolean =
        orSu("settings put secure $key $value") { Settings.Secure.putString(context.contentResolver, key, value) }

    fun putSystem(context: Context, key: String, value: String): Boolean =
        orSu("settings put system $key $value") { Settings.System.putString(context.contentResolver, key, value) }

    // wm overscan ; IWindowManager.setOverscan need WRITE_SECURE_SETTINGS
    fun setOverscan(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        orSu("wm overscan $left,$top,$right,$bottom") {
            val wm = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)!!
            val int = Int::class.javaPrimitiveType
            wm.javaClass.getMethod("setOverscan", int, int, int, int, int)
                .invoke(wm, Display.DEFAULT_DISPLAY, left, top, right, bottom)
            true
        }

    fun setTimeZone(context: Context, id: String): Boolean =
        // root uid may send the protected broadcast ; shell uid may not
        orSu("setprop persist.sys.timezone $id && am broadcast -a android.intent.action.TIMEZONE_CHANGED --es time-zone $id") {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setTimeZone(id)
            true
        }

    fun setTime(context: Context, millis: Long): Boolean =
        orSu("date @${millis / 1000}") {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setTime(millis)
            true
        }

    fun reboot(context: Context) {
        orSu("svc power reboot || reboot") {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).reboot(null)
            true
        }
    }

    // SHUTDOWN = signature|system
    fun powerOff(context: Context) {
        orSu("svc power shutdown || reboot -p") {
            context.startActivity(Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
                .putExtra("android.intent.extra.KEY_CONFIRM", false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
    }

    // FORCE_STOP_PACKAGES = signature|system
    fun forceStop(context: Context, pkg: String): Boolean =
        orSu("am force-stop $pkg") {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.javaClass.getMethod("forceStopPackage", String::class.java).invoke(am, pkg)
            true
        }

    // DELETE_PACKAGES = signature|system ; observer null = fire + forget
    fun uninstall(context: Context, pkg: String): Boolean =
        orSu("pm uninstall $pkg") {
            val pm = context.packageManager
            val observer = Class.forName("android.content.pm.IPackageDeleteObserver")
            pm.javaClass.getMethod("deletePackage", String::class.java, observer, Int::class.javaPrimitiveType)
                .invoke(pm, pkg, null, 0)
            true
        }

    // CLEAR_APP_USER_DATA = signature only => platform key | su
    fun clearData(context: Context, pkg: String): Boolean =
        orSu("pm clear $pkg") {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val observer = Class.forName("android.content.pm.IPackageDataObserver")
            am.javaClass.getMethod("clearApplicationUserData", String::class.java, observer).invoke(am, pkg, null) as Boolean
        }

    // CHANGE_COMPONENT_ENABLED_STATE = signature|system
    fun setEnabled(context: Context, pkg: String, on: Boolean): Boolean =
        orSu("pm ${if (on) "enable" else "disable"} $pkg") {
            context.packageManager.setApplicationEnabledSetting(pkg,
                if (on) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0)
            true
        }

    // MANAGE_DEVICE_ADMINS = signature|system ; DevicePolicyManager.setActiveAdmin = hidden
    private fun activateAdmin(context: Context): Boolean {
        val admin = ComponentName(context, ScreenOffAdminReceiver::class.java)
        return orSu("dpm set-active-admin ${admin.flattenToString()}") {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(admin)) return@orSu true
            dpm.javaClass.getMethod("setActiveAdmin", ComponentName::class.java, Boolean::class.javaPrimitiveType)
                .invoke(dpm, admin, true)
            true
        }
    }

    // everything roundabout needs from android: listener, accessibility, admin
    fun grantAll(context: Context): Boolean {
        val listener = ComponentName(context, MediaListener::class.java).flattenToString()
        val access = ComponentName(context, NavAccessibility::class.java).flattenToString()
        var ok = true
        if (!listenerOn(context)) ok = putSecure(context, "enabled_notification_listeners", listener) && ok
        if (!accessibilityOn(context)) {
            ok = putSecure(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, access) && ok
            ok = putSecure(context, Settings.Secure.ACCESSIBILITY_ENABLED, "1") && ok
        }
        ok = activateAdmin(context) && ok
        return ok
    }

    fun listenerOn(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(context, MediaListener::class.java).flattenToString()) == true

    fun accessibilityOn(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(ComponentName(context, NavAccessibility::class.java).flattenToString()) == true

    fun adminOn(context: Context): Boolean =
        (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isAdminActive(ComponentName(context, ScreenOffAdminReceiver::class.java))
}
