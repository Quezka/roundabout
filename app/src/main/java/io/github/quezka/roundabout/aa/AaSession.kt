package io.github.quezka.roundabout.aa

// ties framing + handshake + AA messages to a byte link ; answers pings,
// auth, service discovery and channel opens ; forwards channel data upstream
class AaSession(
    private val link: ByteLink,
    private val onServiceFrame: (channel: Int, payload: ByteArray) -> Unit,
    private val onState: (AaHandshake.State) -> Unit = {},
) {
    val handshake = AaHandshake()
    val openChannels = HashSet<Int>()
    private val parser = AaFraming.Parser()
    private val reasm = AaFraming.Reassembler()
    @Volatile private var run = true

    fun start() { send(AaControl.CH, AaControl.versionRequest()) }

    private fun send(channel: Int, payload: ByteArray) {
        for (f in AaFraming.encode(channel, payload)) link.write(AaFraming.bytes(f))
    }

    // blocking read loop ; call from a worker thread
    fun pump() {
        val buf = ByteArray(0x4000)
        while (run) {
            val n = try { link.read(buf) } catch (e: Exception) { -1 }
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
            AaControl.MsgId.PING_REQUEST -> send(AaControl.CH, AaControl.pingResponse())
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
            handshake.onControl(AaControl.MsgId.CHANNEL_OPEN_REQUEST, payload)
            onState(handshake.state)
            return
        }
        onServiceFrame(ch, payload)
    }
}
