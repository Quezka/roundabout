package io.github.quezka.roundabout.aa

// input channel: touch events -> InputReport (aap_protobuf/inputsource)
object AaInput {
    object Msg {
        const val INPUT_REPORT = 0x8001
        const val KEY_BINDING_REQUEST = 0x8002
        const val KEY_BINDING_RESPONSE = 0x8003
        const val INPUT_FEEDBACK = 0x8004
    }
    object Action { const val DOWN = 0; const val UP = 1; const val MOVED = 2; const val POINTER_DOWN = 5; const val POINTER_UP = 6 }

    // InputReport { timestamp=1 ; TouchEvent touch_event=3 { pointer_data=1 {x,y,pointer_id} ; action_index=2 ; action=3 } }
    fun touch(x: Int, y: Int, action: Int, timestampNs: Long, pointerId: Int = 0): ByteArray {
        val pointer = Proto.Writer()
            .intAlways(1, x).intAlways(2, y).intAlways(3, pointerId)
            .toByteArray()
        val touch = Proto.Writer()
            .message(1, pointer)
            .int(2, 0)        // action_index
            .intAlways(3, action) // action (0 = DOWN is the default, but send it explicitly)
            .toByteArray()
        val report = Proto.Writer()
            .u64Always(1, timestampNs)
            .message(3, touch)
            .toByteArray()
        return AaAv.message(Msg.INPUT_REPORT, report)
    }
}
