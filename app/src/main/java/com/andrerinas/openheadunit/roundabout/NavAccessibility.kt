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
