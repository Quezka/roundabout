package io.github.quezka.roundabout.domain

import kotlinx.coroutines.flow.Flow

// ambient led colours ; argb
enum class Ambient(val argb: Long) {
    ORANGE(0xFFFF7A1A),
    RED(0xFFE5322B),
    GREEN(0xFF2BB673),
    BLUE(0xFF2F8CFF),
    VIOLET(0xFF9B5CFF),
    WHITE(0xFFEDEDED);

    fun next(): Ambient = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): Ambient = entries.firstOrNull { it.name == name } ?: ORANGE
    }
}

// auto = dark @ night like android auto
enum class ThemeMode { AUTO, LIGHT, DARK;
    fun isDark(hour: Int): Boolean = when (this) {
        AUTO -> hour !in 7..18
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun parse(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

interface DashPrefs {
    fun themeMode(): Flow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)
    fun ambient(): Flow<Ambient>
    fun setAmbient(ambient: Ambient)
    fun rolePackage(role: Role): Flow<String?>
    fun setRolePackage(role: Role, packageName: String?)
    fun favorites(): Flow<List<String>>
    fun setFavorites(packages: List<String>)
}

object Favorites {
    const val MAX = 3

    // toggle pin ; oldest drops when full
    fun toggle(current: List<String>, packageName: String): List<String> =
        if (packageName in current) current - packageName
        else (current + packageName).takeLast(MAX)

    fun encode(list: List<String>): String = list.joinToString(",")
    fun decode(raw: String?): List<String> = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
