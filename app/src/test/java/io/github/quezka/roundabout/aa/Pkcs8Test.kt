package io.github.quezka.roundabout.aa

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class Pkcs8Test {
    @Test fun tlvEncodesShortLength() {
        val t = Pkcs8.tlv(0x04, ByteArray(3))
        assertEquals(0x04, t[0].toInt() and 0xFF)
        assertEquals(0x03, t[1].toInt() and 0xFF)
        assertEquals(5, t.size)
    }

    @Test fun tlvEncodesLongLength() {
        val t = Pkcs8.tlv(0x04, ByteArray(300))
        assertEquals(0x04, t[0].toInt() and 0xFF)
        assertEquals(0x82, t[1].toInt() and 0xFF)
        assertEquals(0x01, t[2].toInt() and 0xFF)
        assertEquals(0x2C, t[3].toInt() and 0xFF) // 300 = 0x012C
        assertEquals(304, t.size)
    }

    // loads the real public GAL head-unit key (PKCS#1) and proves the wrap parses
    @Test fun wrapsRealHeadunitKey() {
        val f = listOf(
            File("src/main/assets/aa/headunit.key"),
            File("app/src/main/assets/aa/headunit.key"),
            File("../../app/src/main/assets/aa/headunit.key"),
        ).firstOrNull { it.exists() } ?: error("headunit.key not found from ${File(".").absolutePath}")
        val pem = f.readText()
        val b64 = pem.replace(Regex("-----[^-]+-----"), "").replace(Regex("\\s"), "")
        val der = Base64.getDecoder().decode(b64)
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Pkcs8.wrapPkcs1(der)))
        assert(key is RSAPrivateKey)
        assert((key as RSAPrivateKey).modulus.bitLength() in 1024..4096)
    }
}
