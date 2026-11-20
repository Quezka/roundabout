package io.github.quezka.roundabout.aa

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

// PCM audio sink (48k / 16-bit / stereo by default)
class AudioSink(
    private val sampleRate: Int = 48000,
    private val channels: Int = 2,
) : AvSink {
    private var track: AudioTrack? = null

    private fun ensure() {
        if (track != null) return
        val chMask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val min = AudioTrack.getMinBufferSize(sampleRate, chMask, AudioFormat.ENCODING_PCM_16BIT)
        @Suppress("DEPRECATION")
        val t = AudioTrack(
            AudioManager.STREAM_MUSIC, sampleRate, chMask,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096), AudioTrack.MODE_STREAM,
        )
        t.play()
        track = t
    }

    override fun onCodecConfig(bytes: ByteArray) {}
    override fun onStart(sessionId: Int, configIndex: Int) { ensure() }
    override fun onData(bytes: ByteArray) { ensure(); track?.write(bytes, 0, bytes.size) }
    override fun onStop() { try { track?.stop() } catch (e: Exception) { } }

    fun release() {
        try { track?.stop(); track?.release() } catch (e: Exception) { }
        track = null
    }
}
