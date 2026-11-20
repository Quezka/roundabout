package io.github.quezka.roundabout.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.quezka.roundabout.di.AppGraph
import io.github.quezka.roundabout.service.KeyguardService

class MainActivity : ComponentActivity() {
    private val vm: DashViewModel by viewModels {
        viewModelFactory { initializer { DashViewModel(AppGraph.get(applicationContext)) } }
    }
    private val request = mutableStateOf(0 to Screen.DASH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) request.value = 1 to screenFrom(intent)
        KeyguardService.start(this)
        RailService.start(this)
        // car dash -> screen stays on while dash shown
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()
        // never show system bars ; re-hide the instant they peek (edge swipe / wake)
        window.decorView.setOnSystemUiVisibilityChangeListener { v ->
            if (v and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) hideBars()
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent {
            val ambient by vm.ambient.collectAsStateWithLifecycle()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val now by vm.now.collectAsStateWithLifecycle()
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
            RoundaboutTheme(Color(ambient.argb), mode.isDark(hour)) { DashApp(vm, version, request.value) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        request.value = (request.value.first + 1) to screenFrom(intent)
    }

    private fun screenFrom(intent: Intent?): Screen =
        when (intent?.getStringExtra(RailService.EXTRA_SCREEN)) {
            RailService.SCREEN_APPS -> Screen.APPS
            RailService.SCREEN_CLIPS -> Screen.CLIPS
            RailService.SCREEN_AA -> Screen.AA
            else -> Screen.DASH
        }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        hideBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    @Deprecated("launcher swallows back")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // dash = home ; back handled by compose BackHandler @ sub screens
        if (onBackPressedDispatcher.hasEnabledCallbacks()) super.onBackPressed()
    }
}
