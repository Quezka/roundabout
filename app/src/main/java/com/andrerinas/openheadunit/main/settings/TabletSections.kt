package com.andrerinas.openheadunit.main.settings

import androidx.annotation.StringRes
import com.andrerinas.openheadunit.R

// roundabout: tablet tab sections ; id = rail category id + icon key @ SettingsCategoryIcons
object TabletSections {
    class Section(val id: String, @StringRes val label: Int)

    val all = listOf(
        Section("tWifi", R.string.rb_sys_wifi),
        Section("tBluetooth", R.string.rb_sys_bt),
        Section("tDisplay", R.string.rb_sys_display),
        Section("tSound", R.string.rb_sys_sound),
        Section("tTime", R.string.rb_sys_date_time),
        Section("tLanguage", R.string.rb_sys_language),
        Section("tApps", R.string.rb_sys_apps),
        Section("tAccess", R.string.rb_sys_access),
        Section("tAbout", R.string.rb_sys_about),
        Section("tPower", R.string.rb_sys_power),
    )
}
