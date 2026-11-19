package io.github.quezka.roundabout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.quezka.roundabout.di.AppGraph
import io.github.quezka.roundabout.domain.Ambient
import io.github.quezka.roundabout.domain.AppCatalog
import io.github.quezka.roundabout.domain.AppEntry
import io.github.quezka.roundabout.domain.Favorites
import io.github.quezka.roundabout.domain.MediaAction
import io.github.quezka.roundabout.domain.MediaState
import io.github.quezka.roundabout.domain.Role
import io.github.quezka.roundabout.domain.RoleDefaults
import io.github.quezka.roundabout.domain.Speed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

@OptIn(ExperimentalCoroutinesApi::class)
class DashViewModel(private val graph: AppGraph) : ViewModel() {
    private val live = SharingStarted.WhileSubscribed(5_000)
    private val mediaRefresh = MutableStateFlow(0)

    val now: StateFlow<Long> = flow {
        while (true) {
            val t = System.currentTimeMillis()
            emit(t)
            // 1/min <- only date + greeting shown ; less cpu
            delay(60_000 - t % 60_000)
        }
    }.stateIn(viewModelScope, live, System.currentTimeMillis())

    val speedKmh: StateFlow<Int?> = graph.speed.speed()
        .transformLatest { mps ->
            emit(Speed.kmh(mps))
            delay(Speed.STALE_MS)
            emit(null)
        }
        .stateIn(viewModelScope, live, null)

    val ambient: StateFlow<Ambient> = graph.prefs.ambient().stateIn(viewModelScope, live, Ambient.ORANGE)

    val mediaAccess = MutableStateFlow(graph.media.hasAccess())

    val media: StateFlow<MediaState> = mediaRefresh
        .flatMapLatest { graph.media.state() }
        .stateIn(viewModelScope, live, MediaState.NONE)

    val apps: StateFlow<List<AppEntry>> = graph.apps.changes()
        .onStart { emit(Unit) }
        .map { AppCatalog.sorted(graph.apps.installed(), graph.selfPackage) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, live, emptyList())

    val navigation: StateFlow<AppEntry?> = combine(apps, graph.prefs.rolePackage(Role.NAVIGATION)) { list, chosen ->
        RoleDefaults.resolve(Role.NAVIGATION, chosen, list)
    }.stateIn(viewModelScope, live, null)

    val music: StateFlow<AppEntry?> = combine(apps, graph.prefs.rolePackage(Role.MUSIC)) { list, chosen ->
        RoleDefaults.resolve(Role.MUSIC, chosen, list)
    }.stateIn(viewModelScope, live, null)

    val favorites: StateFlow<List<AppEntry>> = combine(apps, graph.prefs.favorites()) { list, pins ->
        pins.mapNotNull { p -> list.firstOrNull { it.packageName == p } }
    }.stateIn(viewModelScope, live, emptyList())

    private val favoritePackages = graph.prefs.favorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val icon = graph.icon

    // access may change while user @ settings
    fun onResume() {
        val has = graph.media.hasAccess()
        if (has != mediaAccess.value) {
            mediaAccess.value = has
            mediaRefresh.value++
        }
    }

    fun launch(app: AppEntry) = graph.launcher.launch(app)
    fun media(action: MediaAction) = graph.media.send(action)
    fun cycleAmbient() = graph.prefs.setAmbient(ambient.value.next())
    fun setAmbient(a: Ambient) = graph.prefs.setAmbient(a)
    fun setRole(role: Role, app: AppEntry) = graph.prefs.setRolePackage(role, app.packageName)
    fun toggleFavorite(app: AppEntry) =
        graph.prefs.setFavorites(Favorites.toggle(favoritePackages.value, app.packageName))
    fun openSystemSettings() = graph.launcher.openSystemSettings()
    fun openNotificationAccess() = graph.launcher.openNotificationAccess()
    fun openAppInfo(app: AppEntry) = graph.launcher.openAppInfo(app.packageName)
}
