package io.github.quezka.roundabout.aa

// AV (media sink) channel messages + handler (video/audio share the wire)
object AaAv {
    object Msg {
        const val DATA = 0
        const val CODEC_CONFIG = 1
        const val SETUP = 0x8000
        const val START = 0x8001
        const val STOP = 0x8002
        const val CONFIG = 0x8003
        const val ACK = 0x8004
        const val VIDEO_FOCUS_REQUEST = 0x8007
        const val VIDEO_FOCUS_NOTIFY = 0x8008
    }
    object ConfigStatus { const val WAIT = 1; const val READY = 2 }

    // Setup { required MediaCodecType type = 1 }
    fun setupType(body: ByteArray): Int {
        val r = Proto.Reader(body)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == 1 && w == Proto.Wire.VARINT) return r.varint().toInt()
            r.skip(w)
        }
        return 0
    }

    // Config { required Status status = 1 ; max_unacked = 2 ; repeated uint32 configuration_indices = 3 }
    fun configReady(maxUnacked: Int = 1, indices: IntArray = intArrayOf(0)): ByteArray {
        val w = Proto.Writer().intAlways(1, ConfigStatus.READY)
        if (maxUnacked > 0) w.int(2, maxUnacked)
        for (i in indices) w.intAlways(3, i) // proto2 repeated, unpacked ; 0 is a valid index
        return w.toByteArray()
    }

    // Start { required int32 session_id = 1 ; required uint32 configuration_index = 2 }
    data class Start(val sessionId: Int, val configIndex: Int)
    fun start(body: ByteArray): Start {
        var sid = 0; var idx = 0
        val r = Proto.Reader(body)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            when {
                f == 1 && w == Proto.Wire.VARINT -> sid = r.varint().toInt()
                f == 2 && w == Proto.Wire.VARINT -> idx = r.varint().toInt()
                else -> r.skip(w)
            }
        }
        return Start(sid, idx)
    }

    // Ack { required int32 session_id = 1 ; optional uint32 ack = 2 }
    fun ack(sessionId: Int, ack: Int): ByteArray =
        Proto.Writer().intAlways(1, sessionId).int(2, ack).toByteArray()

    // [msgType:2 BE][body]
    fun message(type: Int, body: ByteArray): ByteArray =
        byteArrayOf((type shr 8).toByte(), type.toByte()) + body

    fun msgType(payload: ByteArray): Int =
        ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
}

// sink for one AV channel ; video = MediaCodec, audio = AudioTrack
interface AvSink {
    fun onCodecConfig(bytes: ByteArray)
    fun onStart(sessionId: Int, configIndex: Int)
    fun onData(bytes: ByteArray)
    fun onStop()
}

// decodes AV channel messages ; replies Setup/Ack on the wire via `send`
class AvChannel(
    private val sink: AvSink,
    private val send: (msgType: Int, body: ByteArray) -> Unit,
) {
    private var sessionId = 0
    var started = false
        private set

    fun onMessage(payload: ByteArray) {
        if (payload.size < 2) return
        val type = AaAv.msgType(payload)
        val body = payload.copyOfRange(2, payload.size)
        when (type) {
            AaAv.Msg.SETUP -> send(AaAv.Msg.CONFIG, AaAv.configReady())
            AaAv.Msg.START -> {
                val s = AaAv.start(body)
                sessionId = s.sessionId
                started = true
                sink.onStart(s.sessionId, s.configIndex)
            }
            AaAv.Msg.STOP -> { started = false; sink.onStop() }
            AaAv.Msg.CODEC_CONFIG -> sink.onCodecConfig(body)
            AaAv.Msg.DATA -> {
                sink.onData(body)
                send(AaAv.Msg.ACK, AaAv.ack(sessionId, 1))
            }
        }
    }
}
