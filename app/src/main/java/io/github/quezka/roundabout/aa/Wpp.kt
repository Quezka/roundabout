package io.github.quezka.roundabout.aa

// WiFi projection (WPP) wire framing ; carried over RFCOMM then TCP+TLS
// header: [2B payload-length BE][2B message-type BE] ; payload = protobuf
object Wpp {
    const val HEADER = 4

    object Type {
        const val START_REQUEST = 1
        const val INFO_REQUEST = 2
        const val INFO_RESPONSE = 3
        const val VERSION_REQUEST = 4
        const val VERSION_RESPONSE = 5
        const val START_RESPONSE = 7
        const val PING = 8
        const val PING_RESPONSE = 9
        const val SETUP_INFO = 11
    }

    data class Msg(val type: Int, val payload: ByteArray)

    fun bytes(m: Msg): ByteArray {
        val out = ByteArray(HEADER + m.payload.size)
        out[0] = ((m.payload.size shr 8) and 0xFF).toByte()
        out[1] = (m.payload.size and 0xFF).toByte()
        out[2] = ((m.type shr 8) and 0xFF).toByte()
        out[3] = (m.type and 0xFF).toByte()
        System.arraycopy(m.payload, 0, out, HEADER, m.payload.size)
        return out
    }

    class Parser {
        private var stash = ByteArray(0)
        fun feed(data: ByteArray, off: Int = 0, len: Int = data.size): List<Msg> {
            val chunk = data.copyOfRange(off, off + len)
            val buf = if (stash.isEmpty()) chunk else stash + chunk
            val msgs = ArrayList<Msg>()
            var i = 0
            while (buf.size - i >= HEADER) {
                val plen = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
                val type = ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
                if (buf.size - i - HEADER < plen) break
                msgs.add(Msg(type, buf.copyOfRange(i + HEADER, i + HEADER + plen)))
                i += HEADER + plen
            }
            stash = if (i < buf.size) buf.copyOfRange(i, buf.size) else ByteArray(0)
            return msgs
        }
    }
}
