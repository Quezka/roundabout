package io.github.quezka.roundabout.aa

// control channel (ch 0) message ids + raw (non-proto) messages
object AaControl {
    const val CH = AaFraming.CH_CONTROL

    object MsgId {
        const val VERSION_REQUEST = 0x0001
        const val VERSION_RESPONSE = 0x0002
        const val SSL_HANDSHAKE = 0x0003
        const val AUTH_COMPLETE = 0x0004
        const val SERVICE_DISCOVERY_REQUEST = 0x0005
        const val SERVICE_DISCOVERY_RESPONSE = 0x0006
        const val CHANNEL_OPEN_REQUEST = 0x0007
        const val CHANNEL_OPEN_RESPONSE = 0x0008
        const val CHANNEL_CLOSE_NOTIFICATION = 0x0009
        const val PING_REQUEST = 0x000b
        const val PING_RESPONSE = 0x000c
        const val NAV_FOCUS_REQUEST = 0x000d
        const val NAV_FOCUS_NOTIFICATION = 0x000e
        const val BYEBYE_REQUEST = 0x000f
        const val BYEBYE_RESPONSE = 0x0010
        const val VOICE_SESSION_NOTIFICATION = 0x0011
        const val AUDIO_FOCUS_REQUEST = 0x0012
        const val AUDIO_FOCUS_NOTIFICATION = 0x0013
    }

    // AA protocol version we advertise (1.7 = broad compat)
    const val MAJOR = 1
    const val MINOR = 7

    // [msgId:2 BE][major:1][minor:1] = 4 bytes
    fun versionRequest(major: Int = MAJOR, minor: Int = MINOR): ByteArray =
        control(MsgId.VERSION_REQUEST, byteArrayOf(major.toByte(), minor.toByte()))

    // [msgId:2 BE][body]
    fun control(msgId: Int, body: ByteArray): ByteArray =
        byteArrayOf((msgId shr 8).toByte(), msgId.toByte()) + body

    data class VersionResponse(val major: Int, val minor: Int, val status: Int)

    // [msgId:2 BE][major:1][minor:1][status:2 BE] = 6 bytes
    fun parseVersionResponse(p: ByteArray): VersionResponse {
        require(p.size >= 6) { "version response < 6 bytes" }
        val major = p[2].toInt() and 0xFF
        val minor = p[3].toInt() and 0xFF
        val status = ((p[4].toInt() and 0xFF) shl 8) or (p[5].toInt() and 0xFF)
        return VersionResponse(major, minor, status)
    }

    fun msgId(payload: ByteArray): Int {
        require(payload.size >= 2) { "control msg < 2 bytes" }
        return ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
    }

    fun ping(): ByteArray = byteArrayOf(0x00, MsgId.PING_REQUEST.toByte())

    // PingResponse { required int64 timestamp = 1 } ; phone expects its timestamp echoed
    fun pingResponse(timestampNs: Long = 0L): ByteArray =
        control(MsgId.PING_RESPONSE, Proto.Writer().u64Always(1, timestampNs).toByteArray())

    fun pingReply(reqPayload: ByteArray): ByteArray = pingResponse(Proto.varintField(reqPayload, 1, from = 2))

    // AudioFocusNotification { required focus_state = 1 } <- AudioFocusRequest { audio_focus_type = 1 }
    // GAIN->GAIN TRANSIENT/DUCK->TRANSIENT RELEASE->LOSS
    fun audioFocusReply(reqPayload: ByteArray): ByteArray {
        val req = Proto.varintField(reqPayload, 1, from = 2).toInt()
        val state = when (req) { 1 -> 1; 2 -> 2; 3 -> 2; 4 -> 3; else -> 0 }
        return control(MsgId.AUDIO_FOCUS_NOTIFICATION, Proto.Writer().intAlways(1, state).toByteArray())
    }

    // NavFocusNotification { required focus_type = 1 } ; default PROJECTED
    fun navFocusReply(reqPayload: ByteArray): ByteArray {
        val t = Proto.varintField(reqPayload, 1, from = 2).toInt()
        val focus = if (t == 0) 2 else t
        return control(MsgId.NAV_FOCUS_NOTIFICATION, Proto.Writer().intAlways(1, focus).toByteArray())
    }

    fun byebyeReply(): ByteArray = control(MsgId.BYEBYE_RESPONSE, ByteArray(0))
}
