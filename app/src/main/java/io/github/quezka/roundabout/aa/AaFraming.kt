package io.github.quezka.roundabout.aa

// AA wire framing (reverse-engineered, see docs/agents/aa.md)
// header: [1B channel][1B flags][2B payload-length BE] ; payload follows
// one logical message may span several frames (FIRST .. LAST) ; each frame <= 16KB
object AaFraming {
    const val HEADER = 4
    const val MAX_FRAME = 0x4000
    const val MAX_PAYLOAD = MAX_FRAME - HEADER

    const val CH_CONTROL = 0

    object Flags {
        const val NONE = 0x00
        const val FIRST = 0x01
        const val LAST = 0x02
        const val ENCRYPTED = 0x08
    }

    data class Frame(val channel: Int, val flags: Int, val payload: ByteArray) {
        val first: Boolean get() = flags and Flags.FIRST != 0
        val last: Boolean get() = flags and Flags.LAST != 0
    }

    // split a logical payload into frames on one channel
    fun encode(channel: Int, payload: ByteArray, flags: Int = Flags.NONE): List<Frame> {
        val n = maxOf(1, (payload.size + MAX_PAYLOAD - 1) / MAX_PAYLOAD)
        val out = ArrayList<Frame>(n)
        for (i in 0 until n) {
            val off = i * MAX_PAYLOAD
            val len = minOf(MAX_PAYLOAD, payload.size - off)
            var f = flags
            if (i == 0) f = f or Flags.FIRST
            if (i == n - 1) f = f or Flags.LAST
            out.add(Frame(channel, f, payload.copyOfRange(off, off + len)))
        }
        return out
    }

    // a frame -> wire bytes (header + payload)
    fun bytes(f: Frame): ByteArray {
        val out = ByteArray(HEADER + f.payload.size)
        out[0] = f.channel.toByte()
        out[1] = f.flags.toByte()
        out[2] = ((f.payload.size shr 8) and 0xFF).toByte()
        out[3] = (f.payload.size and 0xFF).toByte()
        System.arraycopy(f.payload, 0, out, HEADER, f.payload.size)
        return out
    }

    // streaming parser: feed raw bytes -> complete frames (buffers partial header/payload)
    class Parser {
        private var stash = ByteArray(0)
        fun feed(data: ByteArray, off: Int = 0, len: Int = data.size): List<Frame> {
            val chunk = data.copyOfRange(off, off + len)
            val buf = if (stash.isEmpty()) chunk else stash + chunk
            val frames = ArrayList<Frame>()
            var i = 0
            while (buf.size - i >= HEADER) {
                val ch = buf[i].toInt() and 0xFF
                val fl = buf[i + 1].toInt() and 0xFF
                val plen = ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
                if (buf.size - i - HEADER < plen) break
                frames.add(Frame(ch, fl, buf.copyOfRange(i + HEADER, i + HEADER + plen)))
                i += HEADER + plen
            }
            stash = if (i < buf.size) buf.copyOfRange(i, buf.size) else ByteArray(0)
            return frames
        }
    }

    // combine frames of one logical message ; returns complete payload on LAST
    class Reassembler {
        private val acc = java.io.ByteArrayOutputStream()
        private var ch = -1
        fun add(f: Frame): Pair<Int, ByteArray>? {
            if (f.first) { acc.reset(); ch = f.channel }
            acc.write(f.payload)
            if (!f.last) return null
            val out = ch to acc.toByteArray()
            acc.reset()
            return out
        }
    }
}
