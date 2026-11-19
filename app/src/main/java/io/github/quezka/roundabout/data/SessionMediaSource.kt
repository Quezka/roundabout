package io.github.quezka.roundabout.data

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import io.github.quezka.roundabout.domain.MediaAction
import io.github.quezka.roundabout.domain.MediaSource
import io.github.quezka.roundabout.domain.MediaState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// need: notification listener access <- getActiveSessions
class SessionMediaSource(
    private val context: Context,
    private val listener: ComponentName,
) : MediaSource {
    private val sessions = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    @Volatile private var current: MediaController? = null

    override fun hasAccess(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it) == listener }
    }

    override fun state(): Flow<MediaState> = callbackFlow {
        if (!hasAccess()) {
            trySend(MediaState.NONE)
            awaitClose { }
            return@callbackFlow
        }
        val handler = Handler(Looper.getMainLooper())
        var watched: MediaController? = null
        lateinit var pick: (List<MediaController>?) -> Unit
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { trySend(read(watched)) }
            override fun onMetadataChanged(metadata: MediaMetadata?) { trySend(read(watched)) }
            override fun onSessionDestroyed() { pick(active()) }
        }
        pick = { list ->
            val c = list?.firstOrNull { isPlaying(it) } ?: list?.firstOrNull()
            if (c?.sessionToken != watched?.sessionToken) {
                watched?.unregisterCallback(callback)
                watched = c
                c?.registerCallback(callback, handler)
            }
            current = c
            trySend(read(c))
        }
        val changed = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        try {
            sessions.addOnActiveSessionsChangedListener(changed, listener, handler)
            pick(active())
        } catch (e: SecurityException) {
            trySend(MediaState.NONE)
        }
        awaitClose {
            watched?.unregisterCallback(callback)
            try { sessions.removeOnActiveSessionsChangedListener(changed) } catch (e: Exception) { }
            current = null
        }
    }

    override fun send(action: MediaAction) {
        val c = current
        if (c != null) {
            val t = c.transportControls
            when (action) {
                MediaAction.PREVIOUS -> t.skipToPrevious()
                MediaAction.NEXT -> t.skipToNext()
                MediaAction.TOGGLE -> if (isPlaying(c)) t.pause() else t.play()
            }
            return
        }
        // no session -> media key ; last player may resume
        val code = when (action) {
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.TOGGLE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun active(): List<MediaController>? =
        try { sessions.getActiveSessions(listener) } catch (e: SecurityException) { null }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: return false
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
    }

    private fun read(c: MediaController?): MediaState {
        if (c == null) return MediaState.NONE
        val m = c.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        return MediaState(title, artist, isPlaying(c), c.packageName)
    }
}
