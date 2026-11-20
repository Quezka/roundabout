package io.github.quezka.roundabout.aa

// ties framing + handshake + AA messages to a byte link ; answers pings,
// auth, service discovery and channel opens ; forwards channel data upstream.
// when `tls` is set, the tls handshake rides inside control msg 3 and every
// payload after it is a tls record with the ENCRYPTED flag.
class AaSession(
    private val link: ByteLink,
    private val onServiceFrame: (channel: Int, payload: ByteArray) -> Unit,
    private val onState: (AaHandshake.State) -> Unit = {},
    private val onChannelOpen: (channel: Int) -> Unit = {},
    private val log: (String) -> Unit = {},
    private val tls: AaTls? = null,
) {
    val handshake = AaHandshake()
    val openChannels = HashSet<Int>()
    private val parser = AaFraming.Parser()
    private val reasm = AaFraming.Reassembler()
    private val msgEncrypted = HashMap<Int, Boolean>()
    private val writeLock = Any()
    private var tlsStarted = false
    private var authSent = false
    @Volatile private var run = true

    fun start() { send(AaControl.CH, AaControl.versionRequest()) }

    // public write on any channel (av acks, input reports, focus requests)
    fun sendOn(channel: Int, payload: ByteArray) = send(channel, payload)

    private fun send(channel: Int, payload: ByteArray) {
        val t = tls
        // only the ssl handshake + auth complete go out plaintext (see callers)
        if (t != null && t.established) {
            val enc = t.encrypt(payload)
            log("send ch$channel ${payload.size}b enc ${enc.size}b")
            synchronized(writeLock) {
                for (f in AaFraming.encode(channel, enc, AaFraming.Flags.ENCRYPTED)) link.write(AaFraming.bytes(f))
            }
        } else {
            sendPlain(channel, payload)
        }
    }

    // tls handshake bytes travel as plaintext control msg 3
    private fun sendHandshake(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val payload = AaControl.control(AaControl.MsgId.SSL_HANDSHAKE, bytes)
        sendPlain(AaControl.CH, payload)
    }

    private fun sendPlain(channel: Int, payload: ByteArray) {
        log("send ch$channel ${payload.size}b plain")
        synchronized(writeLock) { for (f in AaFraming.encode(channel, payload)) link.write(AaFraming.bytes(f)) }
    }

    // blocking read loop ; call from a worker thread
    fun pump() {
        val buf = ByteArray(0x4000)
        while (run) {
            val n = try {
                link.read(buf)
            } catch (e: java.net.SocketTimeoutException) {
                continue // idle link ; keep the session alive
            } catch (e: Exception) {
                -1
            }
            if (n <= 0) break
            log("recv $n b")
            for (f in parser.feed(buf, 0, n)) handle(f)
        }
        link.close()
    }

    fun stop() { run = false; try { link.close() } catch (e: Exception) { } }

    private fun handle(f: AaFraming.Frame) {
        log("frame ch${f.channel} fl${f.flags} len${f.payload.size}")
        if (f.first) msgEncrypted[f.channel] = (f.flags and AaFraming.Flags.ENCRYPTED) != 0
        val msg = reasm.add(f) ?: return
        val encrypted = msgEncrypted.remove(f.channel) ?: false
        val (ch, raw) = msg
        val payload = if (encrypted) (tls?.decrypt(raw) ?: return) else raw
        if (payload.isEmpty()) return
        if (ch == AaControl.CH) onControl(payload) else onChannel(ch, payload)
    }

    private fun onControl(payload: ByteArray) {
        val id = AaControl.msgId(payload)
        log("control msg 0x${Integer.toHexString(id)} (${payload.size}b)")
        when (id) {
            AaControl.MsgId.PING_REQUEST -> send(AaControl.CH, AaControl.pingReply(payload))
            AaControl.MsgId.AUDIO_FOCUS_REQUEST -> send(AaControl.CH, AaControl.audioFocusReply(payload))
            AaControl.MsgId.NAV_FOCUS_REQUEST -> send(AaControl.CH, AaControl.navFocusReply(payload))
            AaControl.MsgId.BYEBYE_REQUEST -> { send(AaControl.CH, AaControl.byebyeReply()); stop() }
            AaControl.MsgId.VERSION_RESPONSE -> {
                handshake.onControl(id, payload)
                onState(handshake.state)
                // phone agreed on the version -> start tls inside control msg 3
                val t = tls
                if (t != null && !tlsStarted) {
                    tlsStarted = true
                    sendHandshake(t.begin())
                }
            }
            AaControl.MsgId.SSL_HANDSHAKE -> {
                val t = tls ?: return
                val body = payload.copyOfRange(2, payload.size)
                sendHandshake(t.handshake(body))
                if (t.established && !authSent) {
                    authSent = true
                    handshake.authOk(true)
                    // reference hu sends auth complete unencrypted
                    sendPlain(AaControl.CH, AaControl.authComplete(0))
                    onState(handshake.state)
                }
            }
            AaControl.MsgId.AUTH_COMPLETE -> {
                handshake.onControl(id, payload)
                handshake.authOk(AaMessages.authStatus(payload) == AaMessages.Status.SUCCESS)
                onState(handshake.state)
            }
            AaControl.MsgId.SERVICE_DISCOVERY_REQUEST -> {
                handshake.onControl(id, payload)
                send(AaControl.CH, AaControl.control(AaControl.MsgId.SERVICE_DISCOVERY_RESPONSE, AaMessages.serviceDiscoveryResponse()))
                onState(handshake.state)
            }
            else -> { handshake.onControl(id, payload); onState(handshake.state) }
        }
    }

    private fun onChannel(ch: Int, payload: ByteArray) {
        val id = if (payload.size >= 2) AaControl.msgId(payload) else -1
        if (id == AaControl.MsgId.CHANNEL_OPEN_REQUEST) {
            log("ch$ch open request")
            send(ch, AaControl.control(AaControl.MsgId.CHANNEL_OPEN_RESPONSE, AaMessages.channelOpenResponse(AaMessages.Status.SUCCESS)))
            openChannels.add(ch)
            onChannelOpen(ch)
            handshake.onControl(AaControl.MsgId.CHANNEL_OPEN_REQUEST, payload)
            onState(handshake.state)
            return
        }
        onServiceFrame(ch, payload)
    }
}
