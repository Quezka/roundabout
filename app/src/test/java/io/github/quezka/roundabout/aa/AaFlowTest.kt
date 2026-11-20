package io.github.quezka.roundabout.aa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FlowLink(input: ByteArray) : ByteLink {
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

private class FlowSink : AvSink {
    val calls = ArrayList<String>()
    var lastData = ByteArray(0)
    override fun onCodecConfig(bytes: ByteArray) { calls.add("codec") }
    override fun onStart(sessionId: Int, configIndex: Int) { calls.add("start:$sessionId:$configIndex") }
    override fun onData(bytes: ByteArray) { calls.add("data"); lastData = bytes }
    override fun onStop() { calls.add("stop") }
}

// whole phone exchange over one stream, in protocol order
class AaFlowTest {
    private fun frame(ch: Int, payload: ByteArray): ByteArray =
        AaFraming.encode(ch, payload).map { AaFraming.bytes(it) }.reduce { a, b -> a + b }

    private fun msgs(bytes: ByteArray): List<Pair<Int, ByteArray>> {
        val out = ArrayList<Pair<Int, ByteArray>>()
        val parser = AaFraming.Parser()
        val re = AaFraming.Reassembler()
        for (f in parser.feed(bytes, 0, bytes.size)) re.add(f)?.let { out.add(it) }
        return out
    }

    @Test fun phoneExchangeThroughToVideoAck() {
        val phone =
            frame(0, byteArrayOf(0, 2, 1, 7, 0, 0)) +        // VERSION_RESPONSE ok
                frame(0, byteArrayOf(0, 4, 8, 0)) +           // AUTH_COMPLETE status 0
                frame(0, byteArrayOf(0, 5)) +                 // SERVICE_DISCOVERY_REQUEST
                frame(3, byteArrayOf(0, 7, 16, 3)) +          // CHANNEL_OPEN_REQUEST ch3
                frame(3, AaAv.message(AaAv.Msg.SETUP, Proto.Writer().intAlways(1, 0).toByteArray())) +
                frame(3, AaAv.message(AaAv.Msg.START, Proto.Writer().intAlways(1, 5).toByteArray())) +
                frame(3, AaAv.message(AaAv.Msg.DATA, byteArrayOf(1, 2, 3))) +
                frame(8, byteArrayOf(0, 7, 16, 8)) +          // CHANNEL_OPEN_REQUEST ch8
                frame(8, AaAv.message(AaInput.Msg.KEY_BINDING_REQUEST, ByteArray(0)))

        val link = FlowLink(phone)
        val sink = FlowSink()
        var av: AvChannel? = null
        lateinit var s: AaSession
        s = AaSession(
            link = link,
            onServiceFrame = { ch, payload ->
                when (ch) {
                    3 -> av?.onMessage(payload)
                    8 -> if (AaAv.msgType(payload) == AaInput.Msg.KEY_BINDING_REQUEST) {
                        s.sendOn(8, AaInput.keyBindingResponse(0))
                    }
                }
            },
            onChannelOpen = { ch -> if (ch == 3) av = AvChannel(sink) { t, b -> s.sendOn(ch, AaAv.message(t, b)) } },
        )
        s.start()
        s.pump()

        assertEquals(AaHandshake.State.RUNNING, s.handshake.state)
        assertTrue(s.openChannels.containsAll(listOf(3, 8)))
        assertEquals(listOf("start:5:0", "data"), sink.calls)
        assertArrayEquals(byteArrayOf(1, 2, 3), sink.lastData)

        val out = msgs(link.written())
        // version req, discovery resp, ch3 open resp, av config, av ack, ch8 open resp, keybinding resp
        assertEquals(7, out.size)
        assertEquals(0, out[0].first); assertEquals(AaControl.MsgId.VERSION_REQUEST, AaControl.msgId(out[0].second))
        assertEquals(0, out[1].first); assertEquals(AaControl.MsgId.SERVICE_DISCOVERY_RESPONSE, AaControl.msgId(out[1].second))
        assertEquals(3, out[2].first); assertEquals(AaControl.MsgId.CHANNEL_OPEN_RESPONSE, AaControl.msgId(out[2].second))
        assertEquals(3, out[3].first); assertEquals(AaAv.Msg.CONFIG, AaAv.msgType(out[3].second))
        assertEquals(AaAv.ConfigStatus.READY, Proto.varintField(out[3].second, 1, from = 2).toInt())
        assertEquals(3, out[4].first); assertEquals(AaAv.Msg.ACK, AaAv.msgType(out[4].second))
        assertEquals(5L, Proto.varintField(out[4].second, 1, from = 2)) // session id echoed
        assertEquals(8, out[5].first); assertEquals(AaControl.MsgId.CHANNEL_OPEN_RESPONSE, AaControl.msgId(out[5].second))
        assertEquals(8, out[6].first); assertEquals(AaInput.Msg.KEY_BINDING_RESPONSE, AaAv.msgType(out[6].second))

        // discovery lists video, media audio, input, sensor
        val ids = ArrayList<Int>()
        val dr = Proto.Reader(out[1].second.copyOfRange(2, out[1].second.size))
        while (!dr.eof()) {
            val t = dr.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == 1 && w == Proto.Wire.LEN) ids.add(Proto.varintField(dr.bytes(), 1).toInt()) else dr.skip(w)
        }
        assertEquals(listOf(3, 4, 8, 1), ids)
    }
}
