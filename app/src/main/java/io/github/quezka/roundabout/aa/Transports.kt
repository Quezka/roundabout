package io.github.quezka.roundabout.aa

import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

// plain tcp ; AA negotiates its own tls inside control msg 3
class TcpLink(
    private val host: String,
    private val port: Int,
) : ByteLink {
    private var socket: Socket? = null

    fun connect() {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 10_000)
        s.soTimeout = 30_000 // bound dead reads ; pump treats a timeout as idle
        s.tcpNoDelay = true
        socket = s
    }

    override fun write(bytes: ByteArray) {
        val o = socket!!.outputStream
        o.write(bytes); o.flush()
    }

    override fun read(buf: ByteArray): Int = socket!!.inputStream.read(buf)

    override fun close() { try { socket?.close() } catch (e: Exception) { } }
}

// accepting socket for the phone-initiated (native) path ; the phone calls us
class TcpServerLink(private val port: Int, private val bindAddr: String? = null) : ByteLink {
    private var server: java.net.ServerSocket? = null
    private var socket: java.net.Socket? = null


    fun accept(timeoutMs: Int = 60_000) {
        Holder.close() // release the port a previous native attempt may still hold
        val s = java.net.ServerSocket()
        s.reuseAddress = true
        // ipv4 only: a wildcard ipv6 bind on 5.1 refuses the phone's v4 dial
        val host = bindAddr?.let { java.net.InetAddress.getByName(it) }
        s.bind(java.net.InetSocketAddress(host ?: java.net.InetAddress.getByName("0.0.0.0"), port))
        s.soTimeout = timeoutMs
        server = s
        Holder.set(s)
        val c = if (timeoutMs <= 0) s.accept() else { s.soTimeout = timeoutMs; s.accept() }
        c.soTimeout = 30_000
        c.tcpNoDelay = true
        socket = c
    }

    fun accepted(): java.net.Socket? = socket

    override fun write(bytes: ByteArray) { val o = socket!!.outputStream; o.write(bytes); o.flush() }
    override fun read(buf: ByteArray): Int = socket!!.inputStream.read(buf)
    override fun close() {
        try { socket?.close() } catch (e: Exception) { }
        try { server?.close() } catch (e: Exception) { }
    }
}

@Deprecated("AA tls runs inside the control channel, see AaTls")
class TcpTlsLink(
    private val ssl: SSLContext,
    private val host: String,
    private val port: Int,
) : ByteLink {
    private var socket: SSLSocket? = null

    fun connect() {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), 10_000)
        raw.soTimeout = 30_000 // bound a stuck tls handshake + dead reads
        val s = ssl.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        s.useClientMode = true
        s.soTimeout = 30_000
        s.startHandshake()
        socket = s
    }

    override fun write(bytes: ByteArray) {
        val o = socket!!.outputStream
        o.write(bytes); o.flush()
    }

    override fun read(buf: ByteArray): Int = socket!!.inputStream.read(buf)

    override fun close() { try { socket?.close() } catch (e: Exception) { } }
}

// wireless aa puts tls on the socket itself ; the phone dials us but the hu is
// the tls client, exactly like a real head unit (open-headunit wraps it the same way)
class TlsSocketLink(
    private val raw: java.net.Socket,
    ssl: SSLContext,
    private val log: (String) -> Unit = {},
) : ByteLink {
    private val s: SSLSocket = (ssl.socketFactory.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true) as SSLSocket).apply {
        useClientMode = true
        soTimeout = 15_000
        startHandshake()
        soTimeout = 0
    }

    init { log("tls on socket ${s.session.protocol} ${s.session.cipherSuite}") }

    override fun write(bytes: ByteArray) { val o = s.outputStream; o.write(bytes); o.flush() }
    override fun read(buf: ByteArray): Int = s.inputStream.read(buf)
    override fun close() { try { s.close() } catch (e: Exception) { } }
}

// bluetooth rfcomm (WPP handshake)
class BtSppLink(private val device: BluetoothDevice, private val uuid: UUID) : ByteLink {
    private var socket: android.bluetooth.BluetoothSocket? = null

    fun connect() {
        val s = device.createRfcommSocketToServiceRecord(uuid)
        s.connect()
        socket = s
    }

    override fun write(bytes: ByteArray) {
        val o = socket!!.outputStream
        o.write(bytes); o.flush()
    }

    override fun read(buf: ByteArray): Int = socket!!.inputStream.read(buf)

    override fun close() { try { socket?.close() } catch (e: Exception) { } }
}

// usb bulk (AOAP wired)
class UsbLink(
    private val conn: UsbDeviceConnection,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
) : ByteLink {
    override fun write(bytes: ByteArray) { conn.bulkTransfer(epOut, bytes, bytes.size, 5000) }
    override fun read(buf: ByteArray): Int = conn.bulkTransfer(epIn, buf, buf.size, 5000)
    override fun close() { try { conn.close() } catch (e: Exception) { } }
}

// one listener per process ; a second native attempt must release the port
object Holder {
    @Volatile private var sock: java.net.ServerSocket? = null
    fun set(s: java.net.ServerSocket) { close(); sock = s }
    fun close() { try { sock?.close() } catch (e: Exception) { }; sock = null }
}
