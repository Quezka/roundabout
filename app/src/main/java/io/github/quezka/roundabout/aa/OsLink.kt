package io.github.quezka.roundabout.aa

import android.system.Os
import android.system.OsConstants
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

// java's ServerSocket on this rom is AF_INET6 ; the phone's ipv4 syn reaches the
// kernel but never the listener (a pure AF_INET socket does accept it), so the
// wireless endpoint is opened through android.system.Os instead.
class OsServerLink(private val port: Int, private val log: (String) -> Unit = {}) : ByteLink {
    private var listenFd: java.io.FileDescriptor? = null
    private var fd: java.io.FileDescriptor? = null
    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null

    fun accept(): Boolean {
        try {
            val any = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
            val lfd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, 0)
            listenFd = lfd
            Os.setsockoptInt(lfd, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, 1)
            Os.bind(lfd, any, port)
            Os.listen(lfd, 8)
            log("v4 listening $port")
            val cfd = Os.accept(lfd, null)
            fd = cfd
            log("v4 accepted")
            Os.setsockoptInt(cfd, OsConstants.IPPROTO_TCP, OsConstants.TCP_NODELAY, 1)
            input = FileInputStream(cfd)
            output = FileOutputStream(cfd)
            try { Os.close(lfd) } catch (e: Exception) { }
            listenFd = null
            return true
        } catch (e: Exception) {
            log("v4 accept failed: ${e.message}")
            return false
        }
    }

    override fun write(bytes: ByteArray) {
        val o = output ?: return
        o.write(bytes)
        o.flush()
    }

    override fun read(buf: ByteArray): Int = input?.read(buf) ?: -1

    override fun close() {
        try { fd?.let { Os.close(it) } } catch (e: Exception) { }
        try { listenFd?.let { Os.close(it) } } catch (e: Exception) { }
        fd = null
        input = null
        output = null
        listenFd = null
    }
}

// wireless aa puts tls on the socket itself ; the hu is the client, same as a real
// head unit. streams come from Os, so the engine does the framing by hand.
class TlsByteLink(
    private val inner: ByteLink,
    ssl: SSLContext,
    host: String,
    port: Int,
    private val log: (String) -> Unit = {},
) : ByteLink {
    private val engine: SSLEngine = ssl.createSSLEngine(host, port).apply {
        useClientMode = true
        beginHandshake()
    }
    private val app = ByteBuffer.allocate(maxOf(engine.session.applicationBufferSize, 32 * 1024) * 2)
    private val net = ByteBuffer.allocate(engine.session.packetBufferSize * 2 + 256)
    private val peer = ByteBuffer.allocate(engine.session.packetBufferSize * 4 + 512)
    private var eof = false

    private fun tasks() {
        var t = engine.delegatedTask
        while (t != null) { t.run(); t = engine.delegatedTask }
    }

    private fun flushOut() {
        if (net.position() == 0) return
        net.flip()
        val out = ByteArray(net.remaining())
        net.get(out)
        net.clear()
        inner.write(out)
    }

    private fun fill(): Boolean {
        if (eof) return false
        val buf = ByteArray(16 * 1024)
        val n = try { inner.read(buf) } catch (e: Exception) { -1 }
        if (n <= 0) { eof = true; return false }
        peer.put(buf, 0, n)
        return true
    }

    fun handshake() {
        var guard = 0
        while (engine.handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING && guard++ < 200) {
            val st = engine.handshakeStatus
            if (st == SSLEngineResult.HandshakeStatus.NEED_TASK) { tasks(); continue }
            if (st == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                engine.wrap(ByteBuffer.allocate(0), net)
                flushOut()
                continue
            }
            if (st == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                peer.flip()
                if (!peer.hasRemaining()) {
                    peer.compact()
                    if (!fill()) { peer.flip(); peer.compact(); log("tls eof during handshake"); return }
                    peer.flip()
                }
                val r = engine.unwrap(peer, app)
                peer.compact()
                if (r.status == SSLEngineResult.Status.BUFFER_OVERFLOW) { app.clear(); }
                continue
            }
            if (st == SSLEngineResult.HandshakeStatus.FINISHED) break
        }
        log("tls handshake ${engine.handshakeStatus} ${engine.session?.cipherSuite}")
    }

    override fun write(bytes: ByteArray) {
        val src = ByteBuffer.wrap(bytes)
        while (src.hasRemaining()) {
            val r = engine.wrap(src, net)
            if (r.status == SSLEngineResult.Status.BUFFER_OVERFLOW) { net.clear(); continue }
            if (r.status == SSLEngineResult.Status.CLOSED) break
        }
        flushOut()
    }

    override fun read(buf: ByteArray): Int {
        while (true) {
            app.flip()
            if (app.hasRemaining()) {
                val n = minOf(buf.size, app.remaining())
                app.get(buf, 0, n)
                app.compact()
                return n
            }
            app.compact()
            peer.flip()
            if (!peer.hasRemaining()) {
                peer.compact()
                if (!fill()) return -1
                peer.flip()
            }
            val r = engine.unwrap(peer, app)
            peer.compact()
            if (r.status == SSLEngineResult.Status.BUFFER_OVERFLOW) continue
            if (r.status == SSLEngineResult.Status.CLOSED) return -1
            if (r.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) tasks()
            if (r.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                engine.wrap(ByteBuffer.allocate(0), net); flushOut()
            }
        }
    }

    override fun close() { try { engine.closeOutbound() } catch (e: Exception) { }; inner.close() }
}
