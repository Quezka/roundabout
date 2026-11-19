package io.github.quezka.roundabout.domain

import kotlinx.coroutines.flow.Flow

// road = back cam ; cabin = front cam
enum class Lens(val tag: String) { ROAD("road"), CABIN("cabin") }

data class Clip(val name: String, val bytes: Long, val modified: Long)

interface ClipStore {
    fun clips(): List<Clip>
    fun delete(clip: Clip)
    fun freeBytes(): Long
}

object DashcamPolicy {
    const val SEGMENT_MS = 3 * 60 * 1000
    const val BITRATE = 2_500_000
    // loop cap ; ~80 min @ 2.5 Mbit
    const val MAX_BYTES = 1_500L * 1024 * 1024
    // keep free for system
    const val MIN_FREE = 400L * 1024 * 1024

    // oldest first until total <= cap - next segment + free >= min
    fun toDelete(clips: List<Clip>, free: Long, maxBytes: Long = MAX_BYTES, minFree: Long = MIN_FREE): List<Clip> {
        val segment = BITRATE.toLong() / 8 * (SEGMENT_MS / 1000)
        var total = clips.sumOf { it.bytes }
        var freeNow = free
        val out = mutableListOf<Clip>()
        for (c in clips.sortedBy { it.modified }) {
            if (total + segment <= maxBytes && freeNow - segment >= minFree) break
            out += c
            total -= c.bytes
            freeNow += c.bytes
        }
        return out
    }

    fun clipName(lens: Lens, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): String =
        "%04d%02d%02d_%02d%02d%02d_%s.mp4".format(year, month, day, hour, minute, second, lens.tag)
}

interface DashcamPrefs {
    fun autoRecord(): Flow<Boolean>
    fun setAutoRecord(on: Boolean)
}

// camera owner ; one per recording session
interface DashRecorder {
    fun open(): Boolean
    fun start(name: String, onEnd: () -> Unit): Boolean
    fun stopSegment()
    fun close()
}
