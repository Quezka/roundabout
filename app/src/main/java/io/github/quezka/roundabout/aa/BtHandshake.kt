package io.github.quezka.roundabout.aa

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.util.UUID

// real-car path: we advertise the AA SDP record over RFCOMM and answer the
// wifi projection handshake so the phone joins our access point.
class BtHandshake(
    private val adapter: BluetoothAdapter,
    private val creds: Creds,
    private val onStart: (Aaw.Start) -> Unit,
    private val log: (String) -> Unit = {},
) {
    data class Creds(val ssid: String, val password: String, val bssid: String, val ip: String, val port: Int = 5288)

    private var server: BluetoothServerSocket? = null
    private var hfp: BluetoothServerSocket? = null
    private val hfpSockets = java.util.Collections.synchronizedList(mutableListOf<BluetoothSocket>())
    private var thread: Thread? = null

    fun start(): Boolean {
        server = try {
            adapter.listenUsingRfcommWithServiceRecord("AndroidAuto", UUID.fromString(UUID_AA))
        } catch (e: Exception) {
            log("bt listen failed: ${e.message}")
            return false
        }
        thread = Thread {
            val srv = server ?: return@Thread
            while (true) {
                val sock = try { srv.accept() } catch (e: Exception) { break }
                log("bt peer ${sock.remoteDevice?.address}")
                Thread { handle(sock) }.also { it.isDaemon = true }.start()
            }
        }.also { it.isDaemon = true; it.start() }
        // a real car also carries hands-free ; android auto refuses wireless setup
        // against a head unit with no hfp profile at all
        hfp = try {
            adapter.listenUsingRfcommWithServiceRecord("Hands-Free Unit", UUID.fromString(UUID_HFP))
        } catch (e: Exception) {
            log("hfp record refused: ${e.message}")
            null
        }
        if (hfp != null) {
            Thread {
                val srv = hfp ?: return@Thread
                while (true) {
                    val sock = try { srv.accept() } catch (e: Exception) { break }
                    log("hfp peer ${sock.remoteDevice?.address}")
                    // hold it: closing makes the phone see ACL_DISCONNECTED and drop us as a car
                    hfpSockets.add(sock)
                    Thread { serveHfp(sock) }.also { it.isDaemon = true }.start()
                }
            }.also { it.isDaemon = true; it.start() }
            log("hfp record published")
        }
        log("bt listening ${UUID_AA}")
        startPoke()
        return true
    }

    @Volatile private var aaDialled = false
    @Volatile private var running = false

    // wake poke: hu dials phone hfp-ag -> acl + slc up -> gearhead sees car -> dials aa uuid
    // retry `15s` until phone opens aa ; same cadence as open-headunit
    private fun startPoke() {
        running = true
        Thread {
            while (running && !aaDialled) {
                val bonded = try { adapter.bondedDevices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
                for (d in bonded) {
                    if (!running || aaDialled) break
                    // skip if phone already holds hfp to us
                    if (synchronized(hfpSockets) { hfpSockets.any { it.isConnected && it.remoteDevice?.address == d.address } }) continue
                    val major = try { d.bluetoothClass?.majorDeviceClass } catch (e: Exception) { null }
                    if (major != null && major != android.bluetooth.BluetoothClass.Device.Major.PHONE) continue
                    try {
                        val s = d.createRfcommSocketToServiceRecord(UUID.fromString(UUID_HFP_AG))
                        s.connect()
                        log("poke hfp-ag ok ${d.address}")
                        hfpSockets.add(s)
                        Thread { serveHfp(s) }.also { it.isDaemon = true }.start()
                    } catch (e: Exception) {
                        log("poke ${d.address} failed: ${e.message}")
                    }
                }
                try { Thread.sleep(15_000) } catch (e: InterruptedException) { break }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    // hu = hands-free side ; hf speaks first, whoever opened the socket
    // walk: BRSF -> CIND=? -> CIND? -> CMER => slc up ; then `AT+CIND?` keepalive `2s`
    // silent socket => phone hfp state machine half-open -> times out -> no aa dial
    private fun serveHfp(sock: BluetoothSocket) {
        val walk = listOf("AT+BRSF=0", "AT+CIND=?", "AT+CIND?", "AT+CMER=3,0,0,1")
        var step = 0
        val lock = Any()
        fun cmd(s: String) = synchronized(lock) { sock.outputStream.write("$s\r".toByteArray(Charsets.US_ASCII)); sock.outputStream.flush() }
        fun reply(lines: List<String>) = synchronized(lock) {
            lines.forEach { sock.outputStream.write("\r\n$it\r\n".toByteArray(Charsets.US_ASCII)) }
            sock.outputStream.flush()
        }
        try {
            cmd(walk[0])
            val buf = ByteArray(1024)
            while (true) {
                val n = sock.inputStream.read(buf)
                if (n <= 0) break
                for (line in String(buf, 0, n, Charsets.US_ASCII).split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }) {
                    log("hfp rx $line")
                    if (line.startsWith("AT")) { reply(hfpAnswer(line)); continue }
                    val ack = line == "OK" || line.startsWith("ERROR")
                    if (!ack || step >= walk.size) continue
                    step++
                    if (step < walk.size) cmd(walk[step]) else {
                        log("hfp slc up ${sock.remoteDevice?.address}")
                        Thread {
                            try { while (sock.isConnected) { Thread.sleep(2_000); cmd("AT+CIND?") } } catch (e: Exception) { }
                        }.also { it.isDaemon = true }.start()
                    }
                }
            }
        } catch (e: Exception) {
            log("hfp io end: ${e.message}")
        } finally {
            hfpSockets.remove(sock)
            try { sock.close() } catch (e: Exception) { }
        }
    }

    // phone acting as hf against us ; answer like open-headunit `HfpAtResponder`
    private fun hfpAnswer(c: String): List<String> {
        val body = when {
            c.startsWith("AT+BRSF") -> listOf("+BRSF: 20")
            c.startsWith("AT+CIND=?") -> listOf("+CIND: (\"service\",(0,1)),(\"call\",(0,1)),(\"callsetup\",(0,3)),(\"callheld\",(0,2)),(\"signal\",(0,5)),(\"roam\",(0,1)),(\"battchg\",(0,5))")
            c.startsWith("AT+CIND?") -> listOf("+CIND: 1,0,0,0,5,0,5")
            c.startsWith("AT+CHLD=?") -> listOf("+CHLD: (0,1,2,3)")
            c.startsWith("AT+BIND=?") -> listOf("+BIND: (1,2)")
            c.startsWith("AT+BIND?") -> listOf("+BIND: 1,1", "+BIND: 2,1")
            else -> emptyList()
        }
        return body + "OK"
    }

    fun stop() {
        running = false
        try { hfp?.close() } catch (e: Exception) { }
        synchronized(hfpSockets) { hfpSockets.forEach { try { it.close() } catch (e: Exception) { } }; hfpSockets.clear() }
        hfp = null
        try { server?.close() } catch (e: Exception) { }
        server = null
        thread = null
    }

    // real hu order: 4 -> wait 5 (<= `3s`) -> 1 -> wait 2 -> 3 ; phone then sends 7 + 6
    // 3 only as answer to 2 ; phone 1 | 4 = ignore ; 8 -> echo as 9 ; channel held after 3
    private fun handle(sock: BluetoothSocket) {
        aaDialled = true
        val inbox = java.util.concurrent.LinkedBlockingQueue<Wpp.Msg>()
        val eof = Wpp.Msg(-1, ByteArray(0))
        Thread {
            val parser = Wpp.Parser()
            val buf = ByteArray(2048)
            try {
                while (true) {
                    val n = sock.inputStream.read(buf)
                    if (n <= 0) break
                    parser.feed(buf, 0, n).forEach { inbox.put(it) }
                }
            } catch (e: Exception) {
                log("bt io end: ${e.message}")
            }
            inbox.put(eof)
        }.also { it.isDaemon = true }.start()
        try {
            var infoAsked = false
            fun common(m: Wpp.Msg): Boolean {
                log("wpp type ${m.type}")
                when (m.type) {
                    Aaw.PING_REQUEST -> { send(sock, Aaw.PING_RESPONSE, m.payload); return true }
                    Aaw.PING_RESPONSE, Aaw.VERSION_REQUEST -> return true
                }
                return false
            }
            send(sock, Aaw.VERSION_REQUEST, Aaw.versionRequest())
            val until = System.currentTimeMillis() + 3_000
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0) { log("no version response ; go"); break }
                val m = inbox.poll(left, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                if (m === eof) return
                if (common(m)) continue
                if (m.type == Aaw.VERSION_RESPONSE) {
                    val v = Aaw.versionResponse(m.payload)
                    log("phone wpp ${v.major}.${v.minor} status ${v.status}")
                    break
                }
                if (m.type == Aaw.INFO_REQUEST) { infoAsked = true; break }
            }
            send(sock, Aaw.START_REQUEST, Aaw.startRequest(creds.ip, creds.port))
            if (infoAsked) send(sock, Aaw.INFO_RESPONSE, Aaw.infoResponse(creds.ssid, creds.password, creds.bssid))
            while (true) {
                val m = inbox.take()
                if (m === eof) break
                if (common(m)) continue
                when (m.type) {
                    Aaw.INFO_REQUEST -> send(
                        sock, Aaw.INFO_RESPONSE,
                        Aaw.infoResponse(creds.ssid, creds.password, creds.bssid),
                    )
                    Aaw.START_RESPONSE -> {
                        val st = Aaw.startRequest(m.payload)
                        log("phone start response ${st.ip}:${st.port} status ${Proto.varintField(m.payload, 3)}")
                        onStart(st)
                    }
                    Aaw.START_REQUEST -> {
                        // phone asks us to start ; the answer carries the endpoint to dial
                        val st = Aaw.startRequest(m.payload)
                        log("phone start request ${st.ip}:${st.port}")
                        send(sock, Aaw.START_RESPONSE, Aaw.startResponse(creds.ip, creds.port))
                        onStart(Aaw.Start(creds.ip, creds.port))
                    }
                    Aaw.CONNECT_STATUS -> log("phone join status ${Proto.varintField(m.payload, 1)}")
                }
            }
        } finally {
            try { sock.close() } catch (e: Exception) { }
        }
    }

    private fun send(sock: BluetoothSocket, type: Int, body: ByteArray) {
        try {
            sock.outputStream.write(Wpp.bytes(Wpp.Msg(type, body)))
            sock.outputStream.flush()
        } catch (e: Exception) {
            log("bt write failed: ${e.message}")
        }
    }

    companion object {
        const val UUID_AA = "4de17a00-52cb-11e6-bdf4-0800200c9a66"
        private const val UUID_HFP = "0000111e-0000-1000-8000-00805f9b34fb"
        // phone side of hfp ; poke target
        private const val UUID_HFP_AG = "0000111f-0000-1000-8000-00805f9b34fb"
    }
}
