package io.github.quezka.roundabout.aa

// ties framing + handshake + AA messages to a byte link ; answers pings,
// auth, service discovery and channel opens ; forwards channel data upstream
class AaSession(
    private val link: ByteLink,
    private val onServiceFrame: (channel: Int, payload: ByteArray) -> Unit,
    private val onState: (AaHandshake.State) -> Unit = {},
    private val onChannelOpen: (channel: Int) -> Unit = {},
) {
    val handshake = AaHandshake()
    val openChannels = HashSet<Int>()
    private val parser = AaFraming.Parser()
    private val reasm = AaFraming.Reassembler()
    private val writeLock = Any()
    @Volatile private var run = true

    fun start() { send(AaControl.CH, AaControl.versionRequest()) }

    // public write on any channel (av acks, input reports, focus requests)
    fun sendOn(channel: Int, payload: ByteArray) = send(channel, payload)

    private fun send(channel: Int, payload: ByteArray) {
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
            for (f in parser.feed(buf, 0, n)) handle(f)
        }
        link.close()
    }

    fun stop() { run = false; try { link.close() } catch (e: Exception) { } }

    private fun handle(f: AaFraming.Frame) {
        val msg = reasm.add(f) ?: return
        val (ch, payload) = msg
        if (ch == AaControl.CH) onControl(payload) else onChannel(ch, payload)
    }

    private fun onControl(payload: ByteArray) {
        val id = AaControl.msgId(payload)
        when (id) {
            AaControl.MsgId.PING_REQUEST -> send(AaControl.CH, AaControl.pingReply(payload))
            AaControl.MsgId.AUDIO_FOCUS_REQUEST -> send(AaControl.CH, AaControl.audioFocusReply(payload))
            AaControl.MsgId.NAV_FOCUS_REQUEST -> send(AaControl.CH, AaControl.navFocusReply(payload))
            AaControl.MsgId.BYEBYE_REQUEST -> { send(AaControl.CH, AaControl.byebyeReply()); stop() }
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
