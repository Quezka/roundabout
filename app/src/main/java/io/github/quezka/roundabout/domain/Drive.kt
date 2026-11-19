package io.github.quezka.roundabout.domain

import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

interface SpeedSource {
    // metres per second ; null = no fix
    fun speed(): Flow<Float?>
}

object Speed {
    const val GAUGE_MAX_KMH = 160
    // fix older than this = stale -> hide speed
    const val STALE_MS = 5_000L

    fun kmh(metresPerSecond: Float?): Int? =
        metresPerSecond?.takeIf { it.isFinite() && it >= 0f }?.let { (it * 3.6f).roundToInt() }

    fun gaugeFraction(kmh: Int?): Float =
        ((kmh ?: 0).toFloat() / GAUGE_MAX_KMH).coerceIn(0f, 1f)
}

data class PowerState(val percent: Int, val charging: Boolean)

interface PowerSource {
    fun state(): Flow<PowerState>
}

enum class Greeting { MORNING, AFTERNOON, EVENING, NIGHT;
    companion object {
        fun forHour(hour: Int): Greeting = when (hour) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..22 -> EVENING
            else -> NIGHT
        }
    }
}

object DashClock {
    fun time(hour: Int, minute: Int): String =
        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}
