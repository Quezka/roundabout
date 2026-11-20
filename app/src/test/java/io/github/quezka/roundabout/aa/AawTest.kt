package io.github.quezka.roundabout.aa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AawTest {
    @Test fun startRequestFields() {
        val b = Aaw.startRequest("192.168.43.1", 5288)
        assertEquals("192.168.43.1", Aaw.strField(b, 1))
        assertEquals(5288, Proto.varintField(b, 2).toInt())
        assertEquals(0, Proto.varintField(b, 3).toInt())
    }

    @Test fun startRequestParses() {
        val b = Aaw.startRequest("10.0.0.5", 5277)
        val s = Aaw.startRequest(b)
        assertEquals("10.0.0.5", s.ip)
        assertEquals(5277, s.port)
    }

    @Test fun infoResponseCarriesCreds() {
        val b = Aaw.infoResponse("roundabout-aa", "secret", "38:1d:d9:2d:17:6e")
        assertEquals("roundabout-aa", Aaw.strField(b, 1))
        assertEquals("secret", Aaw.strField(b, 2))
        assertEquals("38:1d:d9:2d:17:6e", Aaw.strField(b, 3))
        assertEquals(Aaw.Security.WPA2_PERSONAL, Proto.varintField(b, 4).toInt())
        assertEquals(Aaw.ApType.DYNAMIC, Proto.varintField(b, 5).toInt())
    }

    @Test fun startResponseShape() {
        val b = Aaw.startResponse("192.168.43.1", 5288)
        assertEquals("192.168.43.1", Aaw.strField(b, 1))
        assertEquals(5288, Proto.varintField(b, 2).toInt())
        assertEquals(0, Proto.varintField(b, 3).toInt())
    }

    @Test fun versionRequestShape() {
        val b = Aaw.versionRequest("192.168.43.1", 5288)
        assertEquals(4, Proto.varintField(b, 1).toInt())
        assertEquals(2, Proto.varintField(b, 2).toInt())
        assertTrue(b.isNotEmpty())
    }

    @Test fun versionResponseRoundTrip() {
        val v = Aaw.versionResponse(Aaw.versionResponse())
        assertEquals(4, v.major)
        assertEquals(2, v.minor)
        assertEquals(0, v.status)
    }
}
