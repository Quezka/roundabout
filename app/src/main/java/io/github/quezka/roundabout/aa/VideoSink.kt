package io.github.quezka.roundabout.aa

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

// H.264 (video/avc) decoder ; renders into a Surface (TextureView/SurfaceView)
class VideoSink(
    private val surface: Surface,
    private val width: Int = AaMessages.Video.W,
    private val height: Int = AaMessages.Video.H,
) : AvSink {
    private var codec: MediaCodec? = null
    private val info = MediaCodec.BufferInfo()

    private fun ensure() {
        if (codec != null) return
        codec = try {
            open(width, height)
        } catch (e: Exception) {
            // soc may refuse 720p ; fall back to the smallest advertised profile
            try { open(800, 480) } catch (e2: Exception) { null }
        }
    }

    private fun open(w: Int, h: Int): MediaCodec {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.configure(fmt, surface, null, 0)
        c.start()
        return c
    }

    override fun onCodecConfig(bytes: ByteArray) {
        ensure()
        val c = codec ?: return
        val idx = c.dequeueInputBuffer(10_000)
        if (idx >= 0) {
            val buf = c.getInputBuffer(idx) ?: return
            buf.clear(); buf.put(bytes)
            c.queueInputBuffer(idx, 0, bytes.size, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
        }
    }

    override fun onStart(sessionId: Int, configIndex: Int) { ensure() }

    override fun onData(bytes: ByteArray) {
        ensure()
        val c = codec ?: return
        val idx = c.dequeueInputBuffer(10_000)
        if (idx >= 0) {
            val buf = c.getInputBuffer(idx) ?: return
            buf.clear(); buf.put(bytes)
            c.queueInputBuffer(idx, 0, bytes.size, System.nanoTime() / 1000, 0)
        }
        drain(c)
    }

    private fun drain(c: MediaCodec) {
        while (true) {
            val i = c.dequeueOutputBuffer(info, 0)
            if (i == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (i >= 0) c.releaseOutputBuffer(i, true)
        }
    }

    override fun onStop() {}

    fun release() {
        try { codec?.stop(); codec?.release() } catch (e: Exception) { }
        codec = null
    }
}
