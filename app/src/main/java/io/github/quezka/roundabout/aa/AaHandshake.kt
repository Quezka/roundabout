package io.github.quezka.roundabout.aa

// control-channel handshake state machine ; drives on msgId
// version -> auth(tls) -> service discovery -> channel open -> running
class AaHandshake {
    enum class State { AWAIT_VERSION, AWAIT_AUTH, AWAIT_DISCOVERY, AWAIT_CHANNELS, RUNNING, FAILED }

    var state: State = State.AWAIT_VERSION
        private set
    var versionMajor = 0
        private set
    var versionMinor = 0
        private set

    fun onControl(msgId: Int, payload: ByteArray): State {
        if (state == State.FAILED || state == State.RUNNING) return state
        when (msgId) {
            AaControl.MsgId.VERSION_RESPONSE -> {
                val v = AaControl.parseVersionResponse(payload)
                if (v.status != 0) { state = State.FAILED; return state }
                versionMajor = v.major
                versionMinor = v.minor
                state = State.AWAIT_AUTH
            }
            // AUTH_COMPLETE is protobuf ; caller passes the decoded status via authOk()
            AaControl.MsgId.AUTH_COMPLETE -> { /* wait for authOk() */ }
            AaControl.MsgId.SERVICE_DISCOVERY_REQUEST -> state = State.AWAIT_CHANNELS
            AaControl.MsgId.CHANNEL_OPEN_REQUEST -> state = State.RUNNING
        }
        return state
    }

    // called after AUTH_COMPLETE status decoded (proto) ; true = ok
    fun authOk(ok: Boolean): State {
        state = when {
            !ok -> State.FAILED
            state == State.AWAIT_AUTH -> State.AWAIT_DISCOVERY
            else -> state
        }
        return state
    }

    fun fail(): State { state = State.FAILED; return state }
}
