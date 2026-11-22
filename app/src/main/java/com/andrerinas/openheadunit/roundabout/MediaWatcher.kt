package com.andrerinas.openheadunit.roundabout

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent

data class MediaState(val title: String?, val artist: String?, val playing: Boolean, val packageName: String?) {
    val hasMedia: Boolean get() = !title.isNullOrBlank()

    companion object {
        val NONE = MediaState(null, null, false, null)
    }
}

enum class MediaAction { PREVIOUS, TOGGLE, NEXT }

// port of roundabout `SessionMediaSource` ; callbacks not flow
// need: api >= 21 + notification listener access
class MediaWatcher(private val context: Context, private val onChange: (MediaState) -> Unit) {
    private val listener = ComponentName(context, MediaListener::class.java)
    private var impl: Any? = null // `Impl` ; Any <- class load safe @ api < 21

    fun hasAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it) == listener }
    }

    fun start() {
        stop()
        if (!hasAccess()) { onChange(MediaState.NONE); return }
        impl = Impl().also { it.start() }
    }

    fun stop() {
        (impl as? Impl)?.stop()
        impl = null
    }

    fun send(action: MediaAction) {
        val i = impl as? Impl
        if (i != null && i.send(action)) return
        // no session -> media key ; last player may resume
        val code = when (action) {
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.TOGGLE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private inner class Impl {
        private val sessions = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        private val handler = Handler(Looper.getMainLooper())
        private var watched: MediaController? = null
        private val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = onChange(read(watched))
            override fun onMetadataChanged(metadata: MediaMetadata?) = onChange(read(watched))
            override fun onSessionDestroyed() = pick(active())
        }
        private val changed = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

        fun start() {
            try {
                sessions.addOnActiveSessionsChangedListener(changed, listener, handler)
                pick(active())
            } catch (e: SecurityException) {
                onChange(MediaState.NONE)
            }
        }

        fun stop() {
            watched?.unregisterCallback(callback)
            watched = null
            try { sessions.removeOnActiveSessionsChangedListener(changed) } catch (e: Exception) { }
        }

        fun send(action: MediaAction): Boolean {
            val c = watched ?: return false
            val t = c.transportControls
            when (action) {
                MediaAction.PREVIOUS -> t.skipToPrevious()
                MediaAction.NEXT -> t.skipToNext()
                MediaAction.TOGGLE -> if (isPlaying(c)) t.pause() else t.play()
            }
            return true
        }

        private fun pick(list: List<MediaController>?) {
            val c = list?.firstOrNull { isPlaying(it) } ?: list?.firstOrNull()
            if (c?.sessionToken != watched?.sessionToken) {
                watched?.unregisterCallback(callback)
                watched = c
                c?.registerCallback(callback, handler)
            }
            onChange(read(c))
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
}
