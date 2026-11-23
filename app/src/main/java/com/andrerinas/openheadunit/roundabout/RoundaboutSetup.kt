package com.andrerinas.openheadunit.roundabout

import android.app.Activity
import android.content.Context
import androidx.core.view.ViewCompat
import com.andrerinas.openheadunit.utils.Settings

// one-time fixes when roundabout's layout changes
object RoundaboutSetup {
    private const val INSET_V2 = "rb-rail-overscan-v2"
    private const val TEXTURE_V3 = "rb-texture-view-v3"
    private const val MEDIA_SYNC_V4 = "rb-media-sync-v4"

    fun migrate(context: Context, settings: Settings) {
        val prefs = context.getSharedPreferences(Settings.PREFS_NAME, Context.MODE_PRIVATE)
        // home media card shows android auto's track only through this session
        if (!prefs.getBoolean(MEDIA_SYNC_V4, false)) {
            settings.syncMediaSessionWithAaMetadata = true
            prefs.edit().putBoolean(MEDIA_SYNC_V4, true).apply()
        }
        // no lock screen here (systemui off) ; keyguard state reads "locked" -> input hold blocks aa
        // every start <- auto-optimize / restore can turn it back on
        if (settings.showOverLockScreen) settings.showOverLockScreen = false
        // forced scale stretches the whole frame while the phone keeps a margin for the rail -> offset
        if (settings.forcedScale) settings.forcedScale = false
        // surfaceview lands one overscan strip too far right (5.x) -> textureview, every start
        // <- auto-optimize can pick surface again
        if (settings.viewMode == Settings.ViewMode.SURFACE) settings.viewMode = Settings.ViewMode.TEXTURE
        if (prefs.getBoolean(INSET_V2, false)) return
        // v1 set left inset = rail width for an in-projection rail ; overscan does that now
        if (prefs.getBoolean("rb-aa-rail-inset-v1", false) &&
            settings.insetLeft == RoundaboutRailService.railPx(context)) settings.insetLeft = 0
        prefs.edit().putBoolean(INSET_V2, true).apply()
    }

    // overscan strip (rail): the window frame starts at 0, the decor already pads it,
    // then appcompat's sub-decor pads it again => blank strip beside the rail.
    // sub-decor listener -> pass insets through, no second padding
    fun stripRailInset(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        decor.post {
            val sub = decor.findViewById<android.view.View>(androidx.appcompat.R.id.action_bar_root) ?: return@post
            ViewCompat.setOnApplyWindowInsetsListener(sub) { _, insets -> insets }
            sub.setPadding(0, 0, 0, 0)
            ViewCompat.requestApplyInsets(sub)
        }
    }
}
