package io.github.quezka.roundabout.aa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtoTest {
    @Test fun intVarint() {
        val b = Proto.Writer().int(1, 5).toByteArray()
        assertArrayEquals(byteArrayOf(8, 5), b) // tag (1<<3)|0 = 8
        val r = Proto.Reader(b)
        val t = r.tag()
        assertEquals(1, Proto.field(t))
        assertEquals(0, Proto.wire(t))
        assertEquals(5L, r.varint())
    }

    @Test fun sintNegativeZigzag() {
        val b = Proto.Writer().sint(2, -1).toByteArray()
        assertArrayEquals(byteArrayOf(16, 1), b) // tag (2<<3)|0 = 16 ; zigzag(-1)=1
        val r = Proto.Reader(b)
        r.tag()
        assertEquals(1L, r.varint())
    }

    @Test fun stringField() {
        val b = Proto.Writer().string(3, "hi").toByteArray()
        assertArrayEquals(byteArrayOf(26, 2, 'h'.code.toByte(), 'i'.code.toByte()), b) // (3<<3)|2 = 26
        val r = Proto.Reader(b)
        r.tag()
        assertEquals("hi", r.string())
    }

    @Test fun nestedMessage() {
        val b = Proto.Writer().message(4, Proto.Writer().int(1, 7)).toByteArray()
        assertEquals(34, b[0].toInt() and 0xFF) // (4<<3)|2
        val r = Proto.Reader(b)
        r.tag()
        val sub = Proto.Reader(r.bytes())
        assertEquals(1, Proto.field(sub.tag()))
        assertEquals(7L, sub.varint())
    }

    @Test fun skipUnknownField() {
        val bytes = Proto.Writer().int(1, 1).string(9, "junk").int(2, 2).toByteArray()
        val r = Proto.Reader(bytes)
        val got = LinkedHashMap<Int, Long>()
        while (!r.eof()) {
            val t = r.tag()
            val f = Proto.field(t)
            if (f == 9) r.skip(Proto.wire(t)) else got[f] = r.varint()
        }
        assertEquals(1L, got[1])
        assertEquals(2L, got[2])
    }

    @Test fun zigzagRoundTrip() {
        for (v in listOf(-1000, -1, 0, 1, 1000, Int.MAX_VALUE)) {
            val b = Proto.Writer().sint(1, v).toByteArray()
            if (v == 0) { assertEquals(0, b.size); continue }
            val r = Proto.Reader(b)
            r.tag()
            assertEquals(v, Proto.unzigzag(r.varint()))
        }
    }
}
