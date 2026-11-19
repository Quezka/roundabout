package io.github.quezka.roundabout.aa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeLink(input: ByteArray) : ByteLink {
    private val out = java.io.ByteArrayOutputStream()
    private val src = input.copyOf()
    private var pos = 0
    override fun write(bytes: ByteArray) { out.write(bytes) }
    override fun read(buf: ByteArray): Int {
        if (pos >= src.size) return -1
        val n = minOf(buf.size, src.size - pos)
        System.arraycopy(src, pos, buf, 0, n)
        pos += n
        return n
    }
    override fun close() {}
    fun written(): ByteArray = out.toByteArray()
}

class AaSessionTest {
    private val versionReqFrame = byteArrayOf(0, 3, 0, 4, 0, 1, 1, 7)
    private val pingReqFrame = byteArrayOf(0, 3, 0, 2, 0x00, 0x0b)
    private val pongFrame = byteArrayOf(0, 3, 0, 2, 0x00, 0x0c)

    @Test fun startSendsVersionRequest() {
        val link = FakeLink(ByteArray(0))
        AaSession(link, { _, _ -> }).start()
        assertArrayEquals(versionReqFrame, link.written())
    }

    @Test fun pingGetsPongAndServiceFrameRouted() {
        val svc = byteArrayOf(5, 3, 0, 3, 1, 2, 3) // ch5 FIRST|LAST len3 [1,2,3]
        val link = FakeLink(pingReqFrame + svc)
        val routed = ArrayList<Pair<Int, ByteArray>>()
        val s = AaSession(link, { ch, p -> routed.add(ch to p) })
        s.start()
        s.pump()
        val w = link.written()
        // version request then ping response
        assertTrue(w.size >= versionReqFrame.size + pongFrame.size)
        assertArrayEquals(pongFrame, w.copyOfRange(w.size - pongFrame.size, w.size))
        assertEquals(1, routed.size)
        assertEquals(5, routed[0].first)
        assertArrayEquals(byteArrayOf(1, 2, 3), routed[0].second)
    }

    @Test fun reassemblerHandlesInterleavedChannels() {
        val frames = listOf(
            AaFraming.Frame(1, AaFraming.Flags.FIRST, byteArrayOf(1)),
            AaFraming.Frame(2, AaFraming.Flags.FIRST, byteArrayOf(2)),
            AaFraming.Frame(1, AaFraming.Flags.LAST, byteArrayOf(3)),
            AaFraming.Frame(2, AaFraming.Flags.LAST, byteArrayOf(4)),
        )
        val re = AaFraming.Reassembler()
        val msgs = ArrayList<Pair<Int, ByteArray>>()
        for (f in frames) re.add(f)?.let { msgs.add(it) }
        assertEquals(2, msgs.size)
        assertEquals(1, msgs[0].first)
        assertArrayEquals(byteArrayOf(1, 3), msgs[0].second)
        assertEquals(2, msgs[1].first)
        assertArrayEquals(byteArrayOf(2, 4), msgs[1].second)
    }
}
