package io.github.quezka.roundabout.aa

import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

// tcp + mutual-tls (wireless AA ; phone = tls server, we = client)
class TcpTlsLink(
    private val ssl: SSLContext,
    private val host: String,
    private val port: Int,
) : ByteLink {
    private var socket: SSLSocket? = null

    fun connect() {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), 10_000)
        val s = ssl.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        s.useClientMode = true
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
