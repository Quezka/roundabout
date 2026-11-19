package io.github.quezka.roundabout.aa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AaControlTest {
    @Test fun versionRequestBytes() {
        assertArrayEquals(byteArrayOf(0, 1, 1, 7), AaControl.versionRequest())
        assertArrayEquals(byteArrayOf(0, 1, 1, 6), AaControl.versionRequest(1, 6))
    }

    @Test fun parseVersionResponseOk() {
        val v = AaControl.parseVersionResponse(byteArrayOf(0, 2, 1, 7, 0, 0))
        assertEquals(1, v.major)
        assertEquals(7, v.minor)
        assertEquals(0, v.status)
    }

    @Test fun parseVersionResponseBadStatus() {
        // status = 0xFFFF as BE 2 bytes (negative on the wire)
        val v = AaControl.parseVersionResponse(byteArrayOf(0, 2, 1, 7, 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(0xFFFF, v.status)
    }

    @Test fun msgIdDecode() {
        assertEquals(0x0005, AaControl.msgId(byteArrayOf(0x00, 0x05, 0x42)))
        assertEquals(0x000b, AaControl.msgId(AaControl.ping()))
    }
}

class AaHandshakeTest {
    @Test fun fullHandshakeToRunning() {
        val h = AaHandshake()
        assertEquals(AaHandshake.State.AWAIT_VERSION, h.state)

        h.onControl(AaControl.MsgId.VERSION_RESPONSE, byteArrayOf(0, 2, 1, 7, 0, 0))
        assertEquals(AaHandshake.State.AWAIT_AUTH, h.state)
        assertEquals(1, h.versionMajor)
        assertEquals(7, h.versionMinor)

        h.onControl(AaControl.MsgId.AUTH_COMPLETE, ByteArray(0))
        assertEquals(AaHandshake.State.AWAIT_AUTH, h.state) // status decoded separately
        h.authOk(true)
        assertEquals(AaHandshake.State.AWAIT_DISCOVERY, h.state)

        h.onControl(AaControl.MsgId.SERVICE_DISCOVERY_REQUEST, ByteArray(0))
        assertEquals(AaHandshake.State.AWAIT_CHANNELS, h.state)

        h.onControl(AaControl.MsgId.CHANNEL_OPEN_REQUEST, ByteArray(0))
        assertEquals(AaHandshake.State.RUNNING, h.state)
    }

    @Test fun badVersionFails() {
        val h = AaHandshake()
        h.onControl(AaControl.MsgId.VERSION_RESPONSE, byteArrayOf(0, 2, 1, 7, 0x00, 0x01))
        assertEquals(AaHandshake.State.FAILED, h.state)
    }

    @Test fun badAuthFails() {
        val h = AaHandshake()
        h.onControl(AaControl.MsgId.VERSION_RESPONSE, byteArrayOf(0, 2, 1, 7, 0, 0))
        h.authOk(false)
        assertEquals(AaHandshake.State.FAILED, h.state)
    }

    @Test fun runningIsTerminal() {
        val h = AaHandshake()
        h.onControl(AaControl.MsgId.VERSION_RESPONSE, byteArrayOf(0, 2, 1, 7, 0, 0))
        h.authOk(true)
        h.onControl(AaControl.MsgId.SERVICE_DISCOVERY_REQUEST, ByteArray(0))
        h.onControl(AaControl.MsgId.CHANNEL_OPEN_REQUEST, ByteArray(0))
        assertEquals(AaHandshake.State.RUNNING, h.state)
        // further msgs do not regress
        h.onControl(AaControl.MsgId.VERSION_RESPONSE, byteArrayOf(0, 2, 1, 7, 0, 0))
        assertEquals(AaHandshake.State.RUNNING, h.state)
    }
}

class WppTest {
    @Test fun headerBytes() {
        val b = Wpp.bytes(Wpp.Msg(Wpp.Type.VERSION_REQUEST, byteArrayOf(9, 8)))
        assertEquals(0x00, b[0].toInt() and 0xFF) // len hi
        assertEquals(0x02, b[1].toInt() and 0xFF) // len lo
        assertEquals(0x00, b[2].toInt() and 0xFF) // type hi
        assertEquals(0x04, b[3].toInt() and 0xFF) // type lo
        assertEquals(4 + 2, b.size)
    }

    @Test fun parserSplitChunks() {
        val raw = Wpp.bytes(Wpp.Msg(Wpp.Type.VERSION_REQUEST, byteArrayOf(1, 2, 3))) +
            Wpp.bytes(Wpp.Msg(Wpp.Type.SETUP_INFO, ByteArray(300) { it.toByte() }))
        val p = Wpp.Parser()
        val got = ArrayList<Wpp.Msg>()
        var off = 0
        while (off < raw.size) {
            val n = minOf(7, raw.size - off)
            got += p.feed(raw, off, n)
            off += n
        }
        assertEquals(2, got.size)
        assertEquals(Wpp.Type.VERSION_REQUEST, got[0].type)
        assertArrayEquals(byteArrayOf(1, 2, 3), got[0].payload)
        assertEquals(Wpp.Type.SETUP_INFO, got[1].type)
        assertEquals(300, got[1].payload.size)
    }
}
