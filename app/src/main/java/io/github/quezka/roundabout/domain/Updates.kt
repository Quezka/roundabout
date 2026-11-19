package io.github.quezka.roundabout.domain

// OTA update state + semver compare (pure)
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Downloading(val tag: String) : UpdateState()
    data class Installing(val tag: String) : UpdateState()
    data class Installed(val tag: String) : UpdateState()
    data class Failed(val reason: String) : UpdateState()
}

object UpdateVersion {
    // parse "v1.2.3" / "1.2.3" / "1.2" -> [1,2,3] ; ignore non-digits
    fun parts(tag: String): List<Int> =
        tag.trim().removePrefix("v").split('.', '-', '+')
            .mapNotNull { s -> s.takeWhile { it.isDigit() }.toIntOrNull() }

    fun isNewer(remote: String, current: String): Boolean {
        val r = parts(remote)
        val c = parts(current)
        if (r.isEmpty()) return false
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
