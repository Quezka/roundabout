package io.github.quezka.roundabout.aa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingSink : AvSink {
    val calls = ArrayList<String>()
    var lastData = ByteArray(0)
    override fun onCodecConfig(bytes: ByteArray) { calls.add("codec") }
    override fun onStart(sessionId: Int, configIndex: Int) { calls.add("start:$sessionId:$configIndex") }
    override fun onData(bytes: ByteArray) { calls.add("data"); lastData = bytes }
    override fun onStop() { calls.add("stop") }
}

class AaAvTest {
    private fun fields(bytes: ByteArray): Map<Int, List<Long>> {
        val out = LinkedHashMap<Int, MutableList<Long>>()
        val r = Proto.Reader(bytes)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (w == Proto.Wire.VARINT) out.getOrPut(f) { mutableListOf() }.add(r.varint()) else r.skip(w)
        }
        return out
    }

    @Test fun setupType() {
        assertEquals(3, AaAv.setupType(Proto.Writer().intAlways(1, 3).toByteArray()))
    }

    @Test fun configReadyBytes() {
        val f = fields(AaAv.configReady(maxUnacked = 1, indices = intArrayOf(0, 1)))
        assertEquals(listOf(2L), f[1])       // STATUS_READY
        assertEquals(listOf(1L), f[2])       // max_unacked
        assertEquals(listOf(0L, 1L), f[3])   // indices incl 0
    }

    @Test fun startDecode() {
        val s = AaAv.start(Proto.Writer().intAlways(1, 7).intAlways(2, 0).toByteArray())
        assertEquals(7, s.sessionId)
        assertEquals(0, s.configIndex)
    }

    @Test fun ackBytes() {
        val f = fields(AaAv.ack(sessionId = 7, ack = 1))
        assertEquals(listOf(7L), f[1])
        assertEquals(listOf(1L), f[2])
    }

    @Test fun avChannelFlow() {
        val sent = ArrayList<Pair<Int, ByteArray>>()
        val sink = RecordingSink()
        val ch = AvChannel(sink) { t, b -> sent.add(t to b) }

        ch.onMessage(AaAv.message(AaAv.Msg.SETUP, Proto.Writer().intAlways(1, AaAv.Msg.DATA).toByteArray()))
        assertEquals(listOf(AaAv.Msg.CONFIG), sent.map { it.first })

        ch.onMessage(AaAv.message(AaAv.Msg.START, Proto.Writer().intAlways(1, 5).intAlways(2, 0).toByteArray()))
        assertTrue(ch.started)
        assertEquals("start:5:0", sink.calls.last())

        ch.onMessage(AaAv.message(AaAv.Msg.DATA, byteArrayOf(9, 8, 7)))
        assertArrayEquals(byteArrayOf(9, 8, 7), sink.lastData)
        assertEquals("data", sink.calls.last())
        assertEquals(2, sent.size)
        assertEquals(AaAv.Msg.ACK, sent[1].first)

        ch.onMessage(AaAv.message(AaAv.Msg.STOP, ByteArray(0)))
        assertFalse(ch.started)
        assertEquals("stop", sink.calls.last())
    }
}
