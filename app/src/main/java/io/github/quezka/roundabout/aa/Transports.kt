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
