package io.github.quezka.roundabout.aa

// raw byte pipe ; transport-agnostic (usb bulk / bt rfcomm / tcp+tls)
interface ByteLink {
    fun write(bytes: ByteArray)
    // blocking read into buf ; returns bytes read, -1 on eof
    fun read(buf: ByteArray): Int
    fun close()
}
