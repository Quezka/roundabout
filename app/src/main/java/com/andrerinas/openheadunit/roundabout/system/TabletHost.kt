package com.andrerinas.openheadunit.roundabout.system

import android.app.Activity
import android.os.Build
import android.widget.LinearLayout
import android.widget.ScrollView

// tablet tab content @ roundabout settings ; one panel live at a time
class TabletHost(private val activity: Activity, private val scroll: ScrollView) {
    private val ui = RbUi(activity)
    private var panel: Panel? = null
    var section: String? = null
        private set

    fun show(id: String) {
        if (Build.VERSION.SDK_INT < 21) return
        if (id == section && panel != null) return
        section = id
        panel?.stop()
        scroll.removeAllViews()
        val content = ui.vertical()
        scroll.addView(content)
        content.addView(ui.title(activity.getString(TabletSettingsActivity.labelOf(id))))
        val body = ui.vertical()
        content.addView(body)
        panel = when (id) {
            "tBluetooth" -> BluetoothPanel(ui)
            "tDisplay" -> DisplayPanel(ui)
            "tSound" -> SoundPanel(ui)
            "tTime" -> TimePanel(ui)
            "tLanguage" -> LanguagePanel(ui) { activity.recreate() }
            "tApps" -> AppsPanel(ui)
            "tAccess" -> AccessPanel(ui)
            "tAbout" -> AboutPanel(ui)
            "tPower" -> PowerPanel(ui)
            else -> WifiPanel(ui)
        }.also { it.attach(body) }
        scroll.scrollTo(0, 0)
    }

    fun hide() {
        panel?.stop()
        panel = null
        section = null
        scroll.removeAllViews()
    }
}
