package io.github.quezka.roundabout.aa

// PKCS#1 -> PKCS#8 wrapping ; android KeyFactory wants PKCS#8, headunit.key is PKCS#1
object Pkcs8 {
    private val ALG_RSA = byteArrayOf(
        0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(),
        0x0D, 0x01, 0x01, 0x01, 0x05, 0x00,
    )
    private val VERSION_0 = byteArrayOf(0x02, 0x01, 0x00)

    fun wrapPkcs1(pkcs1: ByteArray): ByteArray =
        tlv(0x30, VERSION_0 + ALG_RSA + tlv(0x04, pkcs1))

    fun tlv(tag: Int, body: ByteArray): ByteArray {
        val n = body.size
        val len = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
            else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
        }
        return byteArrayOf(tag.toByte()) + len + body
    }
}
