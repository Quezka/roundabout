package io.github.quezka.roundabout.aa

import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

// AA carries TLS *inside* control message 3 (SSL_HANDSHAKE), not on the socket.
// the hu is the tls client ; after the handshake every frame payload is a tls
// record with the ENCRYPTED flag set.
class AaTls(ssl: SSLContext, host: String, port: Int = 5277, private val log: (String) -> Unit = {}, serverMode: Boolean = false) {
    private val engine: SSLEngine = ssl.createSSLEngine(host, port).apply {
        useClientMode = !serverMode
        needClientAuth = serverMode
        beginHandshake()
    }
    private var netOut = ByteBuffer.allocate(engine.session.packetBufferSize * 2 + 256)
    private var appOut = ByteBuffer.allocate(maxOf(engine.session.applicationBufferSize, 32 * 1024) * 2)
    private var peerIn = ByteBuffer.allocate(engine.session.packetBufferSize * 4 + 512)
    private val empty = ByteBuffer.allocate(0)

    @Volatile var established = false
        private set

    private fun grow(b: ByteBuffer, need: Int): ByteBuffer {
        if (b.capacity() >= need) return b
        val bigger = ByteBuffer.allocate(maxOf(need, b.capacity() * 2))
        b.flip()
        bigger.put(b)
        return bigger
    }

    private fun runTasks() {
        var t = engine.delegatedTask
        while (t != null) { t.run(); t = engine.delegatedTask }
    }

    private fun drainOut(): ByteArray {
        netOut.flip()
        val out = ByteArray(netOut.remaining())
        netOut.get(out)
        netOut.clear()
        return out
    }

    // first flight (client hello) ; a server produces nothing until the peer talks
    fun begin(): ByteArray {
        var st = engine.handshakeStatus
        if (st == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            val r = engine.wrap(empty, netOut)
            if (r.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                netOut = grow(netOut, netOut.capacity() * 2)
                engine.wrap(empty, netOut)
            }
        } else if (st == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            runTasks()
            val r = engine.wrap(empty, netOut)
            if (r.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                netOut = grow(netOut, netOut.capacity() * 2)
                engine.wrap(empty, netOut)
            }
        }
        return drainOut()
    }

    // feed peer handshake bytes ; returns bytes to send back
    fun handshake(bytes: ByteArray): ByteArray {
        peerIn = grow(peerIn, peerIn.position() + bytes.size + engine.session.packetBufferSize)
        peerIn.put(bytes)
        peerIn.flip()
        var progress = true
        while (progress) {
            progress = false
            if (peerIn.hasRemaining()) {
                val r = engine.unwrap(peerIn, appOut)
                when (r.status) {
                    SSLEngineResult.Status.OK -> progress = true
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        appOut = grow(appOut, appOut.capacity() * 2)
                        progress = true
                    }
                    else -> {}
                }
                var st = r.handshakeStatus
                while (st == SSLEngineResult.HandshakeStatus.NEED_TASK) { runTasks(); st = engine.handshakeStatus }
                if (st == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    val w = engine.wrap(empty, netOut)
                    if (w.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        netOut = grow(netOut, netOut.capacity() * 2)
                        engine.wrap(empty, netOut)
                    }
                    progress = true
                }
            }
        }
        peerIn.compact()
        if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) established = true
        log("tls status=${engine.handshakeStatus} established=$established")
        return drainOut()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val src = ByteBuffer.wrap(plain)
        netOut = grow(netOut, plain.size + engine.session.packetBufferSize * 2 + 256)
        while (src.hasRemaining()) {
            val r = engine.wrap(src, netOut)
            if (r.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                netOut = grow(netOut, netOut.capacity() * 2)
            } else if (r.status == SSLEngineResult.Status.CLOSED) {
                break
            }
        }
        return drainOut()
    }

    fun decrypt(record: ByteArray): ByteArray {
        peerIn = grow(peerIn, peerIn.position() + record.size + engine.session.packetBufferSize)
        peerIn.put(record)
        peerIn.flip()
        appOut.clear()
        while (peerIn.hasRemaining()) {
            val r = engine.unwrap(peerIn, appOut)
            when (r.status) {
                SSLEngineResult.Status.OK -> {}
                SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                    val used = appOut.position()
                    val bigger = ByteBuffer.allocate(appOut.capacity() * 2)
                    appOut.flip(); bigger.put(appOut); appOut = bigger
                    appOut.position(used)
                }
                else -> break
            }
        }
        peerIn.compact()
        appOut.flip()
        val out = ByteArray(appOut.remaining())
        appOut.get(out)
        appOut.clear()
        return out
    }

    fun close() {
        try { engine.closeOutbound() } catch (e: Exception) { }
    }
}
