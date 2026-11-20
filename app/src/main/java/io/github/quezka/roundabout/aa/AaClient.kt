package io.github.quezka.roundabout.aa

import android.content.Context
import android.view.Surface

// drives a full wireless-AA session: transport -> handshake -> channels -> sinks
class AaClient(private val ctx: Context) {

    enum class Phase { IDLE, CONNECTING, HANDSHAKE, RUNNING, FAILED }

    @Volatile var phase: Phase = Phase.IDLE
        private set
    @Volatile var detail: String = ""
        private set

    private var thread: Thread? = null
    @Volatile private var session: AaSession? = null
    private var link: TcpTlsLink? = null
    private var videoSink: VideoSink? = null
    private var audioSink: AudioSink? = null
    private var video: AvChannel? = null
    private var audio: AvChannel? = null
    private var sensors: SensorChannel? = null
    @Volatile private var inputOpen = false
    @Volatile private var night = true
    @Volatile private var surface: Surface? = null

    private fun set(p: Phase, d: String) { phase = p; detail = d }

    fun connectWireless(host: String, port: Int, surface: Surface, w: Int = 800, h: Int = 480, night: Boolean = true) {
        disconnect()
        this.surface = surface
        this.night = night
        set(Phase.CONNECTING, "$host:$port")
        val t = Thread {
            try {
                val tls = HeadunitTls.context(ctx)
                val l = TcpTlsLink(tls, host, port)
                l.connect()
                link = l
                val s = AaSession(
                    link = l,
                    onServiceFrame = { ch, payload -> route(ch, payload) },
                    onState = { st ->
                        when (st) {
                            AaHandshake.State.FAILED -> set(Phase.FAILED, "handshake failed")
                            AaHandshake.State.RUNNING -> set(Phase.RUNNING, "projecting")
                            else -> set(Phase.HANDSHAKE, st.name)
                        }
                    },
                    onChannelOpen = { ch -> onOpen(ch, w, h) },
                )
                session = s
                s.start()
                s.pump()
                set(Phase.IDLE, "disconnected")
            } catch (e: Exception) {
                set(Phase.FAILED, e.message ?: e.javaClass.simpleName)
            }
        }
        t.isDaemon = true
        thread = t
        t.start()
    }

    private fun route(ch: Int, payload: ByteArray) {
        when (ch) {
            AaMessages.Ch.SENSOR -> sensors?.onMessage(payload)
            AaMessages.Ch.MEDIA_SINK_VIDEO -> video?.onMessage(payload)
            AaMessages.Ch.MEDIA_SINK_MEDIA_AUDIO,
            AaMessages.Ch.MEDIA_SINK_SYSTEM_AUDIO,
            AaMessages.Ch.MEDIA_SINK_GUIDANCE_AUDIO -> audio?.onMessage(payload)
            AaMessages.Ch.INPUT_SOURCE -> onInputMessage(payload)
        }
    }

    private fun onOpen(ch: Int, w: Int, h: Int) {
        val s = session ?: return
        when (ch) {
            AaMessages.Ch.SENSOR -> {
                val sensor = SensorChannel({ type, body -> s.sendOn(ch, AaAv.message(type, body)) }, night)
                sensors = sensor
                // phone may gate its ui on driving status ; send both up front
                s.sendOn(ch, AaAv.message(AaSensors.Msg.BATCH, AaSensors.drivingStatusBatch()))
                s.sendOn(ch, AaAv.message(AaSensors.Msg.BATCH, AaSensors.nightModeBatch(night)))
            }
            AaMessages.Ch.MEDIA_SINK_VIDEO -> {
                val surf = surface ?: return
                val sink = VideoSink(surf, w, h)
                videoSink = sink
                video = AvChannel(sink) { type, body -> s.sendOn(ch, AaAv.message(type, body)) }
                s.sendOn(ch, AaAv.videoFocusRequest())
            }
            AaMessages.Ch.MEDIA_SINK_MEDIA_AUDIO -> {
                val sink = AudioSink()
                audioSink = sink
                audio = AvChannel(sink) { type, body -> s.sendOn(ch, AaAv.message(type, body)) }
            }
            AaMessages.Ch.INPUT_SOURCE -> {
                inputOpen = true
                s.sendOn(ch, AaInput.keyBindingResponse(0))
            }
        }
    }

    private fun onInputMessage(payload: ByteArray) {
        if (payload.size < 2) return
        if (AaAv.msgType(payload) == AaInput.Msg.KEY_BINDING_REQUEST) {
            session?.sendOn(AaMessages.Ch.INPUT_SOURCE, AaInput.keyBindingResponse(0))
        }
    }

    // call from the ui thread ; coordinates in 800x480 video space
    fun touch(x: Int, y: Int, action: Int) {
        if (!inputOpen) return
        session?.sendOn(AaMessages.Ch.INPUT_SOURCE, AaInput.touch(x, y, action, System.nanoTime()))
    }

    fun disconnect() {
        try { session?.stop() } catch (e: Exception) { }
        session = null
        videoSink?.release(); videoSink = null
        audioSink?.release(); audioSink = null
        video = null; audio = null
        sensors = null
        inputOpen = false
        try { link?.close() } catch (e: Exception) { }
        link = null
    }
}
