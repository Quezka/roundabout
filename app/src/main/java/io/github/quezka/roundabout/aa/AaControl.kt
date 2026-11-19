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
        const val PING_REQUEST = 0x000b
        const val PING_RESPONSE = 0x000c
        const val NAV_FOCUS_REQUEST = 0x000d
        const val NAV_FOCUS_RESPONSE = 0x000e
        const val SHUTDOWN_REQUEST = 0x000f
        const val SHUTDOWN_RESPONSE = 0x0010
        const val VOICE_SESSION_REQUEST = 0x0011
        const val AUDIO_FOCUS_REQUEST = 0x0012
        const val AUDIO_FOCUS_RESPONSE = 0x0013
    }

    // AA protocol version we advertise (1.7 = broad compat)
    const val MAJOR = 1
    const val MINOR = 7

    // [msgId:2 BE][major:1][minor:1] = 4 bytes
    fun versionRequest(major: Int = MAJOR, minor: Int = MINOR): ByteArray =
        byteArrayOf(0x00, 0x01, major.toByte(), minor.toByte())

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

    fun pingResponse(): ByteArray = byteArrayOf(0x00, MsgId.PING_RESPONSE.toByte())
}
