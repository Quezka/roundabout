package io.github.quezka.roundabout.data

import android.content.Context
import android.content.SharedPreferences
import io.github.quezka.roundabout.domain.Ambient
import io.github.quezka.roundabout.domain.DashPrefs
import io.github.quezka.roundabout.domain.DashcamPrefs
import io.github.quezka.roundabout.domain.Favorites
import io.github.quezka.roundabout.domain.Role
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SharedDashPrefs(context: Context) : DashPrefs, DashcamPrefs {
    private val sp = context.getSharedPreferences("dash", Context.MODE_PRIVATE)

    override fun ambient(): Flow<Ambient> = watch(KEY_AMBIENT) { Ambient.parse(sp.getString(KEY_AMBIENT, null)) }

    override fun setAmbient(ambient: Ambient) {
        sp.edit().putString(KEY_AMBIENT, ambient.name).apply()
    }

    override fun rolePackage(role: Role): Flow<String?> = watch(roleKey(role)) { sp.getString(roleKey(role), null) }

    override fun setRolePackage(role: Role, packageName: String?) {
        sp.edit().putString(roleKey(role), packageName).apply()
    }

    override fun favorites(): Flow<List<String>> = watch(KEY_FAVORITES) { Favorites.decode(sp.getString(KEY_FAVORITES, null)) }

    override fun setFavorites(packages: List<String>) {
        sp.edit().putString(KEY_FAVORITES, Favorites.encode(packages)).apply()
    }

    override fun autoRecord(): Flow<Boolean> = watch(KEY_AUTO_REC) { sp.getBoolean(KEY_AUTO_REC, false) }

    override fun setAutoRecord(on: Boolean) {
        sp.edit().putBoolean(KEY_AUTO_REC, on).apply()
    }

    private fun <T> watch(key: String, read: () -> T): Flow<T> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k == key) trySend(read()) }
        trySend(read())
        sp.registerOnSharedPreferenceChangeListener(l)
        awaitClose { sp.unregisterOnSharedPreferenceChangeListener(l) }
    }

    private fun roleKey(role: Role) = "role_" + role.name.lowercase()

    private companion object {
        const val KEY_AMBIENT = "ambient"
        const val KEY_FAVORITES = "favorites"
        const val KEY_AUTO_REC = "dashcam_auto"
    }
}
