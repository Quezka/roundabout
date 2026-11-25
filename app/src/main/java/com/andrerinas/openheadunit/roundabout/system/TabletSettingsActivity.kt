package com.andrerinas.openheadunit.roundabout.system

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.main.SettingsActivity
import com.andrerinas.openheadunit.main.settings.TabletSections

// `android.settings.*` catcher <- stock settings app gone @ rom
// no own ui => roundabout settings, tablet tab, matching section
class TabletSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_TABLET_SECTION, sectionFor(intent))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    companion object {
        fun labelOf(id: String): Int = TabletSections.all.firstOrNull { it.id == id }?.label ?: R.string.rb_sys_wifi

        fun sectionFor(intent: Intent?): String = when (intent?.action) {
            Settings.ACTION_BLUETOOTH_SETTINGS -> "tBluetooth"
            Settings.ACTION_DISPLAY_SETTINGS -> "tDisplay"
            Settings.ACTION_SOUND_SETTINGS -> "tSound"
            Settings.ACTION_DATE_SETTINGS -> "tTime"
            Settings.ACTION_LOCALE_SETTINGS, Settings.ACTION_INPUT_METHOD_SETTINGS -> "tLanguage"
            Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
            Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> "tApps"
            Settings.ACTION_ACCESSIBILITY_SETTINGS, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            "android.settings.USAGE_ACCESS_SETTINGS", Settings.ACTION_SECURITY_SETTINGS,
            "android.settings.action.MANAGE_OVERLAY_PERMISSION", "android.settings.action.MANAGE_WRITE_SETTINGS" -> "tAccess"
            Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Settings.ACTION_MEMORY_CARD_SETTINGS -> "tAbout"
            else -> "tWifi"
        }
    }
}
