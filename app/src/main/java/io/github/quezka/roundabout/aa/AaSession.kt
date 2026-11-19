package io.github.quezka.roundabout.aa

// ties framing + handshake to a byte link ; routes control vs service frames
class AaSession(
    private val link: ByteLink,
    private val onServiceFrame: (channel: Int, payload: ByteArray) -> Unit,
    private val onState: (AaHandshake.State) -> Unit = {},
) {
    val handshake = AaHandshake()
    private val parser = AaFraming.Parser()
    private val reasm = AaFraming.Reassembler()
    @Volatile private var run = true

    fun start() {
        send(AaControl.CH, AaControl.versionRequest())
    }

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
        if (ch == AaControl.CH) onControl(payload) else onServiceFrame(ch, payload)
    }

    private fun onControl(payload: ByteArray) {
        val id = AaControl.msgId(payload)
        if (id == AaControl.MsgId.PING_REQUEST) { send(AaControl.CH, AaControl.pingResponse()); return }
        handshake.onControl(id, payload)
        onState(handshake.state)
    }

    // caller decodes AUTH_COMPLETE proto status then calls this
    fun authOk(ok: Boolean) {
        handshake.authOk(ok)
        onState(handshake.state)
    }
}
