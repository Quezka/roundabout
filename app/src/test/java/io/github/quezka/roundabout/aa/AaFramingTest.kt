package io.github.quezka.roundabout.aa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaFramingTest {
    private val MAX = AaFraming.MAX_PAYLOAD

    @Test fun smallPayloadIsOneFrame() {
        val p = byteArrayOf(1, 2, 3)
        val frames = AaFraming.encode(7, p)
        assertEquals(1, frames.size)
        assertEquals(7, frames[0].channel)
        assertTrue(frames[0].first)
        assertTrue(frames[0].last)
        assertArrayEquals(p, frames[0].payload)
    }

    @Test fun emptyPayloadIsOneEmptyFrame() {
        val frames = AaFraming.encode(0, ByteArray(0))
        assertEquals(1, frames.size)
        assertTrue(frames[0].first)
        assertTrue(frames[0].last)
        assertEquals(0, frames[0].payload.size)
    }

    @Test fun largePayloadFragmentsCorrectly() {
        val p = ByteArray(MAX * 2 + 5) { (it and 0xFF).toByte() }
        val frames = AaFraming.encode(3, p)
        assertEquals(3, frames.size)
        assertTrue(frames[0].first)
        assertFalse(frames[0].last)
        assertFalse(frames[1].first)
        assertFalse(frames[1].last)
        assertFalse(frames[2].first)
        assertTrue(frames[2].last)
        assertEquals(MAX, frames[0].payload.size)
        assertEquals(MAX, frames[1].payload.size)
        assertEquals(5, frames[2].payload.size)
        assertArrayEquals(p, frames.fold(ByteArray(0)) { a, f -> a + f.payload })
    }

    @Test fun headerBytesAreBigEndian() {
        val f = AaFraming.Frame(0x12, AaFraming.Flags.FIRST or AaFraming.Flags.LAST, ByteArray(0x0102))
        val b = AaFraming.bytes(f)
        assertEquals(0x12, b[0].toInt() and 0xFF)
        assertEquals(0x03, b[1].toInt() and 0xFF)
        assertEquals(0x01, b[2].toInt() and 0xFF) // length hi
        assertEquals(0x02, b[3].toInt() and 0xFF) // length lo
        assertEquals(4 + 0x0102, b.size)
    }

    @Test fun parserHandlesSplitChunks() {
        val raw = AaFraming.encode(1, byteArrayOf(9, 9, 9)).map { AaFraming.bytes(it) }.reduce { a, b -> a + b } +
            AaFraming.encode(2, byteArrayOf(7, 7)).map { AaFraming.bytes(it) }.reduce { a, b -> a + b }
        val parser = AaFraming.Parser()
        val got = ArrayList<AaFraming.Frame>()
        var off = 0
        while (off < raw.size) {
            val n = minOf(3, raw.size - off) // tiny chunks force header splits
            got += parser.feed(raw, off, n)
            off += n
        }
        assertEquals(2, got.size)
        assertEquals(1, got[0].channel)
        assertEquals(2, got[1].channel)
        assertArrayEquals(byteArrayOf(9, 9, 9), got[0].payload)
        assertArrayEquals(byteArrayOf(7, 7), got[1].payload)
    }

    @Test fun roundTripThroughReassembler() {
        val p = ByteArray(MAX + 100) { (it * 7 and 0xFF).toByte() }
        val frames = AaFraming.encode(5, p)
        val re = AaFraming.Reassembler()
        var result: Pair<Int, ByteArray>? = null
        for (f in frames) result = re.add(f) ?: result
        assertEquals(5, result!!.first)
        assertArrayEquals(p, result!!.second)
    }
}
