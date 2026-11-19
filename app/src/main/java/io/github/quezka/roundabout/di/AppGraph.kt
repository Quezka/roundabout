package io.github.quezka.roundabout.di

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import io.github.quezka.roundabout.data.AppIcons
import io.github.quezka.roundabout.data.BatteryPowerSource
import io.github.quezka.roundabout.data.CameraRecorder
import io.github.quezka.roundabout.data.FileClipStore
import io.github.quezka.roundabout.data.GpsSpeedSource
import io.github.quezka.roundabout.data.IntentAppLauncher
import io.github.quezka.roundabout.data.PackageAppSource
import io.github.quezka.roundabout.data.SessionMediaSource
import io.github.quezka.roundabout.data.SharedDashPrefs
import io.github.quezka.roundabout.domain.AppLauncher
import io.github.quezka.roundabout.domain.AppSource
import io.github.quezka.roundabout.domain.ClipStore
import io.github.quezka.roundabout.domain.DashPrefs
import io.github.quezka.roundabout.domain.DashRecorder
import io.github.quezka.roundabout.domain.DashcamPrefs
import io.github.quezka.roundabout.domain.Lens
import io.github.quezka.roundabout.domain.MediaSource
import io.github.quezka.roundabout.domain.PowerSource
import io.github.quezka.roundabout.domain.SpeedSource
import io.github.quezka.roundabout.service.MediaListener

// composition root ; only place that sees data/
class AppGraph(context: Context) {
    private val app = context.applicationContext
    val selfPackage: String = app.packageName
    val apps: AppSource = PackageAppSource(app)
    val launcher: AppLauncher = IntentAppLauncher(app)
    val speed: SpeedSource = GpsSpeedSource(app)
    val power: PowerSource = BatteryPowerSource(app)
    private val shared = SharedDashPrefs(app)
    val prefs: DashPrefs = shared
    val dashcamPrefs: DashcamPrefs = shared
    private val clipFiles = FileClipStore(app)
    val clips: ClipStore = clipFiles
    val clipDir: String = clipFiles.dir.path
    // new recorder per session ; camera1 = single owner
    fun recorder(lens: Lens): DashRecorder = CameraRecorder(clipFiles.dir, lens)
    val media: MediaSource = SessionMediaSource(app, ComponentName(app, MediaListener::class.java))
    private val iconCache = AppIcons(app)
    val icon: (packageName: String, activity: String, sizePx: Int) -> Bitmap? = iconCache::icon

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) { instance ?: AppGraph(context).also { instance = it } }
    }
}
