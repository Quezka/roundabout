package io.github.quezka.roundabout.domain

import kotlinx.coroutines.flow.Flow

data class MediaState(
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val packageName: String?,
) {
    val hasMedia: Boolean get() = !title.isNullOrBlank()

    companion object {
        val NONE = MediaState(null, null, false, null)
    }
}

enum class MediaAction { PREVIOUS, TOGGLE, NEXT }

interface MediaSource {
    fun hasAccess(): Boolean
    fun state(): Flow<MediaState>
    fun send(action: MediaAction)
}
