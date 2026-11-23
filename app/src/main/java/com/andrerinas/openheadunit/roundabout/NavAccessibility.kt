package com.andrerinas.openheadunit.roundabout

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// rail back button + foreground app ; no systemui => only way back out of other apps
class NavAccessibility : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // ime popups + own overlays != foreground app
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        if (pkg == packageName && event.className?.toString()?.startsWith("android.widget") == true) return
        foreground.value = pkg
    }

    // systemui off => audioservice drops every volume key (no volume ui to show first)
    // -> keys handled here ; android auto keeps them if the user sends volume to the phone
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        if (code != android.view.KeyEvent.KEYCODE_VOLUME_UP && code != android.view.KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (com.andrerinas.openheadunit.utils.Settings(this).volumeKeysToPhone &&
            RoundaboutRailService.projectionOnTop) return false
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            RoundaboutRailService.volumeStep(this, code == android.view.KeyEvent.KEYCODE_VOLUME_UP)
        }
        return true
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: NavAccessibility? = null
        private val foreground = MutableStateFlow<String?>(null)
        val current: StateFlow<String?> get() = foreground

        val connected: Boolean get() = instance != null

        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
    }
}
