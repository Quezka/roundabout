package io.github.quezka.roundabout.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyguardService.start(this)
        // car dash -> screen stays on while dash shown
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent {
            val ambient by vm.ambient.collectAsStateWithLifecycle()
            RoundaboutTheme(Color(ambient.argb)) { DashApp(vm, version, homePresses.intValue) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
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
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    @Deprecated("launcher swallows back")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // dash = home ; back handled by compose BackHandler @ sub screens
        if (onBackPressedDispatcher.hasEnabledCallbacks()) super.onBackPressed()
    }
}
