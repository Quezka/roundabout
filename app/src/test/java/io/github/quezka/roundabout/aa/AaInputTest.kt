package io.github.quezka.roundabout.aa

import org.junit.Assert.assertEquals
import org.junit.Test

class AaInputTest {
    @Test fun touchReportStructure() {
        val bytes = AaInput.touch(100, 200, AaInput.Action.DOWN, 123456789L, 0)
        assertEquals(AaInput.Msg.INPUT_REPORT, AaAv.msgType(bytes))

        var ts = 0L
        var touch: ByteArray? = null
        val r = Proto.Reader(bytes.copyOfRange(2, bytes.size))
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            when {
                f == 1 && w == Proto.Wire.VARINT -> ts = r.varint()
                f == 3 && w == Proto.Wire.LEN -> touch = r.bytes()
                else -> r.skip(w)
            }
        }
        assertEquals(123456789L, ts)

        var action = -1
        var ptr: ByteArray? = null
        val tr = Proto.Reader(touch!!)
        while (!tr.eof()) {
            val t = tr.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            when {
                f == 1 && w == Proto.Wire.LEN -> ptr = tr.bytes()
                f == 3 && w == Proto.Wire.VARINT -> action = tr.varint().toInt()
                else -> tr.skip(w)
            }
        }
        assertEquals(AaInput.Action.DOWN, action)

        var x = -1; var y = -1
        val pr = Proto.Reader(ptr!!)
        while (!pr.eof()) {
            val t = pr.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (w == Proto.Wire.VARINT) { val v = pr.varint().toInt(); if (f == 1) x = v else if (f == 2) y = v } else pr.skip(w)
        }
        assertEquals(100, x)
        assertEquals(200, y)
    }
}
