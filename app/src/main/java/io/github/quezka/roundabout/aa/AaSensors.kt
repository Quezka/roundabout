package io.github.quezka.roundabout.aa

// sensor channel (ch1) ; phone requests a type, we answer + push batches.
// driving status matters: restricted status makes the phone block its ui.
object AaSensors {
    object Msg {
        const val REQUEST = 0x8001
        const val RESPONSE = 0x8002
        const val BATCH = 0x8003
        const val ERROR = 0x8004
    }
    const val LOCATION = 1
    const val SPEED = 3
    const val NIGHT_MODE = 10
    const val DRIVING_STATUS = 13

    object DriveStatus {
        const val UNRESTRICTED = 0
        const val NO_VIDEO = 1
        const val NO_KEYBOARD_INPUT = 2
        const val NO_VOICE_INPUT = 4
    }

    // SensorRequest { required SensorType type = 1 }
    fun requestType(body: ByteArray): Int = Proto.varintField(body, 1).toInt()

    // SensorResponse { required MessageStatus status = 1 }
    fun responseBody(status: Int = 0): ByteArray = Proto.Writer().intAlways(1, status).toByteArray()

    // SensorBatch { repeated DrivingStatusData driving_status_data = 13 }
    fun drivingStatusBatch(status: Int = DriveStatus.UNRESTRICTED): ByteArray =
        Proto.Writer().message(13, Proto.Writer().intAlways(1, status).toByteArray()).toByteArray()

    // SensorBatch { repeated NightModeData night_mode_data = 10 { bool night_mode = 1 } }
    fun nightModeBatch(night: Boolean): ByteArray =
        Proto.Writer().message(10, Proto.Writer().bool(1, night).toByteArray()).toByteArray()

    // SensorBatch { repeated SpeedData speed_data = 3 { int32 speed_e3 = 1 } }
    fun speedBatch(kmh: Double): ByteArray =
        Proto.Writer().message(3, Proto.Writer().intAlways(1, (kmh * 1000).toInt()).toByteArray()).toByteArray()

    // SensorBatch { location_data = 1 { latitude_e7=2 longitude_e7=3 accuracy_e3=4
    //               altitude_e2=5 speed_e3=6 bearing_e6=7 } + optional speed_data = 3 }
    fun locationBatch(
        latE7: Int,
        lonE7: Int,
        accuracyM: Float = 0f,
        altitudeM: Double = 0.0,
        speedMps: Float = 0f,
        bearingDeg: Float = 0f,
        speedKmh: Int? = null,
    ): ByteArray {
        val loc = Proto.Writer().intAlways(2, latE7).intAlways(3, lonE7)
        if (accuracyM > 0f) loc.int(4, (accuracyM * 1000f).toInt())
        loc.int(5, (altitudeM * 100.0).toInt())
        if (speedMps > 0f) loc.int(6, (speedMps * 1000f).toInt())
        if (bearingDeg > 0f) loc.int(7, (bearingDeg * 1_000_000f).toInt())
        val w = Proto.Writer().message(1, loc.toByteArray())
        if (speedKmh != null) w.message(3, Proto.Writer().intAlways(1, speedKmh * 1000).toByteArray())
        return w.toByteArray()
    }
}

// answers sensor requests and pushes the values the phone needs
class SensorChannel(private val send: (type: Int, body: ByteArray) -> Unit, initialNight: Boolean = true) {
    var night = initialNight
        private set

    fun onMessage(payload: ByteArray) {
        if (payload.size < 2) return
        if (AaAv.msgType(payload) != AaSensors.Msg.REQUEST) return
        val type = AaSensors.requestType(payload.copyOfRange(2, payload.size))
        send(AaSensors.Msg.RESPONSE, AaSensors.responseBody(0))
        when (type) {
            AaSensors.DRIVING_STATUS -> send(AaSensors.Msg.BATCH, AaSensors.drivingStatusBatch())
            AaSensors.NIGHT_MODE -> send(AaSensors.Msg.BATCH, AaSensors.nightModeBatch(night))
        }
    }

    fun setNight(value: Boolean) {
        night = value
        send(AaSensors.Msg.BATCH, AaSensors.nightModeBatch(value))
    }
}
