package io.github.quezka.roundabout.domain

import kotlinx.coroutines.flow.Flow

data class AppEntry(val packageName: String, val activity: String, val label: String)

enum class Role { NAVIGATION, MUSIC }

interface AppSource {
    fun installed(): List<AppEntry>
    // emits each time apps are added/removed
    fun changes(): Flow<Unit>
}

interface AppLauncher {
    fun launch(app: AppEntry)
    fun openSystemSettings()
    fun openNotificationAccess()
    fun openAppInfo(packageName: String)
}

object AppCatalog {
    fun sorted(apps: List<AppEntry>, self: String): List<AppEntry> =
        apps.filter { it.packageName != self }
            .distinctBy { it.packageName to it.activity }
            .sortedBy { it.label.lowercase() }
}

object RoleDefaults {
    // order = preference ; first installed wins
    val navigation = listOf(
        "app.organicmaps",
        "com.google.android.apps.maps",
        "com.waze",
        "net.osmand.plus",
        "net.osmand",
        "org.navitproject.navit",
        "com.yandex.yandexnavi",
        "ru.yandex.yandexnavi",
    )
    val music = listOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "ru.yandex.music",
        "deezer.android.app",
        "com.vkontakte.android",
        "com.android.music",
    )

    fun resolve(role: Role, chosen: String?, installed: List<AppEntry>): AppEntry? {
        if (chosen != null) installed.firstOrNull { it.packageName == chosen }?.let { return it }
        val prefs = if (role == Role.NAVIGATION) navigation else music
        return prefs.firstNotNullOfOrNull { p -> installed.firstOrNull { it.packageName == p } }
    }
}
