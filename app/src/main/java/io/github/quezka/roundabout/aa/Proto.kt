package io.github.quezka.roundabout.aa

// minimal protobuf wire codec (proto2/3) ; no dependency, api21-safe
// wire types: 0 varint 1 fixed64 2 length-delimited 5 fixed32
object Proto {
    object Wire { const val VARINT = 0; const val FIXED64 = 1; const val LEN = 2; const val FIXED32 = 5 }

    class Writer {
        private val out = java.io.ByteArrayOutputStream()

        fun int(field: Int, v: Int): Writer = u64(field, v.toLong())
        // encode even when 0 (required proto2 fields)
        fun intAlways(field: Int, v: Int): Writer { tag(field, Wire.VARINT); varint(v.toLong()); return this }
        fun u64Always(field: Int, v: Long): Writer { tag(field, Wire.VARINT); varint(v); return this }
        fun u64(field: Int, v: Long): Writer {
            if (v != 0L) { tag(field, Wire.VARINT); varint(v) }
            return this
        }
        fun bool(field: Int, v: Boolean): Writer = if (v) u64(field, 1) else this
        fun boolAlways(field: Int, v: Boolean): Writer = intAlways(field, if (v) 1 else 0)
        fun sint(field: Int, v: Int): Writer {
            if (v != 0) { tag(field, Wire.VARINT); varint(zigzag(v)) }
            return this
        }
        fun bytes(field: Int, b: ByteArray): Writer { tag(field, Wire.LEN); varint(b.size.toLong()); out.write(b); return this }
        fun string(field: Int, s: String): Writer = bytes(field, s.toByteArray(Charsets.UTF_8))
        fun message(field: Int, w: Writer): Writer = bytes(field, w.toByteArray())
        fun message(field: Int, body: ByteArray): Writer = bytes(field, body)
        fun toByteArray(): ByteArray = out.toByteArray()

        private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
        private fun varint(v: Long) {
            var x = v
            while (true) {
                if (x and 0x7FL.inv() == 0L) { out.write((x and 0x7F).toInt()); return }
                out.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
        }
        private fun zigzag(v: Int): Long = ((v.toLong() shl 1) xor (v.toLong() shr 31)) and 0xFFFFFFFFL
    }

    class Reader(private val buf: ByteArray) {
        var pos = 0
            private set

        fun eof() = pos >= buf.size
        fun varint(): Long {
            var r = 0L; var s = 0
            while (true) {
                val b = buf[pos++].toInt() and 0xFF
                r = r or ((b and 0x7F).toLong() shl s)
                if (b and 0x80 == 0) return r
                s += 7
            }
        }
        fun tag(): Int = varint().toInt()
        fun bytes(): ByteArray { val n = varint().toInt(); val b = buf.copyOfRange(pos, pos + n); pos += n; return b }
        fun string(): String = String(bytes(), Charsets.UTF_8)
        fun skip(wire: Int) {
            when (wire) {
                Wire.VARINT -> varint()
                Wire.FIXED64 -> pos += 8
                Wire.LEN -> { val n = varint().toInt(); pos += n }
                Wire.FIXED32 -> pos += 4
                else -> throw IllegalArgumentException("wire $wire")
            }
        }
    }

    fun updateTag(tag: Int): Int = tag
    fun field(tag: Int): Int = tag ushr 3
    fun wire(tag: Int): Int = tag and 7
    fun unzigzag(v: Long): Int = ((v ushr 1) xor -(v and 1)).toInt()

    // first varint value of `field` in body, skipping `from` leading bytes (msgId prefix)
    fun varintField(body: ByteArray, field: Int, from: Int = 0): Long {
        if (body.size <= from) return 0L
        val r = Reader(body.copyOfRange(from, body.size))
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == field && w == Wire.VARINT) return r.varint()
            r.skip(w)
        }
        return 0L
    }
}
