package io.github.quezka.roundabout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.quezka.roundabout.R
import io.github.quezka.roundabout.domain.Greeting
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class Screen { DASH, APPS, SETTINGS }

@Composable
fun DashApp(vm: DashViewModel, version: String, request: Pair<Int, Screen>) {
    var screen by rememberSaveable { mutableStateOf(Screen.DASH) }
    // home press -> back to dash
    LaunchedEffect(request) { if (request.first > 0) screen = request.second }
    BackHandler(screen != Screen.DASH) { screen = Screen.DASH }

    val now by vm.now.collectAsStateWithLifecycle()
    val speed by vm.speedKmh.collectAsStateWithLifecycle()
    val ambient by vm.ambient.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val access by vm.mediaAccess.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val nav by vm.navigation.collectAsStateWithLifecycle()
    val music by vm.music.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val autoRec by vm.autoRecord.collectAsStateWithLifecycle()
    val recording by DashcamService.recording.collectAsStateWithLifecycle()
    val lenses by DashcamService.lenses.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    when (screen) {
        Screen.DASH -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val locale = Locale.getDefault()
            val dateFormat = remember(locale) {
                SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
            }
            val greeting = when (Greeting.forHour(cal.get(Calendar.HOUR_OF_DAY))) {
                Greeting.MORNING -> R.string.greeting_morning
                Greeting.AFTERNOON -> R.string.greeting_afternoon
                Greeting.EVENING -> R.string.greeting_evening
                Greeting.NIGHT -> R.string.greeting_night
            }
            DashScreen(
                DashUi(
                    date = dateFormat.format(cal.time).replaceFirstChar { it.titlecase(locale) },
                    greeting = stringResource(greeting),
                    speedKmh = speed,
                    media = media,
                    mediaAccess = access,
                    navigation = nav,
                    music = music,
                    player = media.packageName?.let { p -> apps.firstOrNull { it.packageName == p } },
                ),
                icon = vm.icon,
            ) { intent ->
                when (intent) {
                    DashIntent.Navigate -> nav?.let(vm::launch) ?: run { screen = Screen.APPS }
                    DashIntent.OpenPlayer -> (media.packageName?.let { p -> apps.firstOrNull { it.packageName == p } } ?: music)
                        ?.let(vm::launch) ?: run { screen = Screen.APPS }
                    DashIntent.Light -> vm.cycleAmbient()
                    DashIntent.Settings -> screen = Screen.SETTINGS
                    DashIntent.GrantMedia -> vm.openNotificationAccess()
                    is DashIntent.Media -> vm.media(intent.action)
                }
            }
        }
        Screen.APPS -> AppsScreen(
            apps = apps,
            favorites = favorites,
            icon = vm.icon,
            onBack = { screen = Screen.DASH },
            onLaunch = vm::launch,
            onSetRole = vm::setRole,
            onToggleFavorite = vm::toggleFavorite,
            onInfo = vm::openAppInfo,
        )
        Screen.SETTINGS -> SettingsScreen(
            ambient = ambient,
            version = version,
            onBack = { screen = Screen.DASH },
            onAmbient = vm::setAmbient,
            onSystemSettings = vm::openSystemSettings,
            onNotificationAccess = vm::openNotificationAccess,
            dashcam = DashcamUi(auto = autoRec, recording = recording, lenses = lenses.map { it.tag }, folder = vm.clipDir),
            onAutoRecord = vm::setAutoRecord,
            onToggleRecording = { DashcamService.toggle(context) },
        )
    }
}
