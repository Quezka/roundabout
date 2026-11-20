package io.github.quezka.roundabout.aa

// AAW over RFCOMM ; field numbers from open-headunit's wireless.proto (gearhead 17.x)
object Aaw {
    const val START_REQUEST = 1
    const val INFO_REQUEST = 2
    const val INFO_RESPONSE = 3
    const val VERSION_REQUEST = 4
    const val VERSION_RESPONSE = 5
    const val CONNECT_STATUS = 6
    const val START_RESPONSE = 7
    // ping: phone may send any time ; answer = echo payload as type 9
    const val PING_REQUEST = 8
    const val PING_RESPONSE = 9

    // SecurityMode: OPEN=1 WPA_PERSONAL=4 WPA2_PERSONAL=8
    object Security { const val OPEN = 1; const val WPA_PERSONAL = 4; const val WPA2_PERSONAL = 8 }
    // AccessPointType: STATIC=0 DYNAMIC=1
    object ApType { const val STATIC = 0; const val DYNAMIC = 1 }

    fun strField(body: ByteArray, field: Int): String? {
        val r = Proto.Reader(body)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == field && w == Proto.Wire.LEN) return r.string()
            r.skip(w)
        }
        return null
    }

    // WifiStartRequest { ip_address=1 port=2 reason=3 }
    data class Start(val ip: String, val port: Int)
    fun startRequest(body: ByteArray): Start =
        Start(strField(body, 1) ?: "", Proto.varintField(body, 2).toInt())

    fun startRequest(ip: String, port: Int): ByteArray = Proto.Writer()
        .string(1, ip)
        .intAlways(2, port)
        .intAlways(3, 0) // START_REQUEST_REASON_USER_REQUEST
        .toByteArray()

    // WifiInfoResponse { ssid=1 key=2 bssid=3 security_mode=4 access_point_type=5 }
    fun infoResponse(
        ssid: String,
        password: String,
        bssid: String,
        security: Int = Security.WPA2_PERSONAL,
        apType: Int = ApType.DYNAMIC,
    ): ByteArray = Proto.Writer()
        .string(1, ssid)
        .string(2, password)
        .string(3, bssid)
        .intAlways(4, security)
        .intAlways(5, apType)
        .toByteArray()

    // WifiVersionRequest { major=1 minor=2 channel_type=3 car_info=5 wpp_info=6 }
    // wpp_info = wpp-over-tcp endpoint ; phone stores it + dials it next time - rfcomm
    // ! ip = null unless a wpp tcp server listens there ; `5288` = aap != wpp
    fun versionRequest(
        ip: String? = null,
        port: Int = 0,
        make: String = "Google",
        model: String = "Desktop Head Unit",
    ): ByteArray {
        val car = Proto.Writer()
            .string(1, make)
            .string(2, model)
            .string(3, "2026")
            .string(4, "RB-01")
            .string(5, make)
            .string(6, model)
            .string(7, "1")
            .string(8, "1.0")
            .toByteArray()
        val w = Proto.Writer()
            .intAlways(1, 4)  // major 4
            .intAlways(2, 2)  // minor 2
            .intAlways(3, 2)  // CHANNELS_DUAL_BAND
            .message(5, car)
        if (ip != null) w.message(6, Proto.Writer().string(1, ip).intAlways(2, port).toByteArray())
        return w.toByteArray()
    }

    // WifiVersionResponse { major=1 minor=2 serial=3 status=4 }
    data class Version(val major: Int, val minor: Int, val status: Int)
    fun versionResponse(body: ByteArray): Version = Version(
        Proto.varintField(body, 1).toInt(),
        Proto.varintField(body, 2).toInt(),
        Proto.varintField(body, 4).toInt(),
    )

    // WifiVersionResponse { major=1 minor=2 serial=3 status=4 }
    fun versionResponse(major: Int = 4, minor: Int = 2, serial: Int = 1, status: Int = 0): ByteArray =
        Proto.Writer()
            .intAlways(1, major)
            .intAlways(2, minor)
            .intAlways(3, serial)
            .intAlways(4, status)
            .toByteArray()

    // WifiStartResponse { ip=1 port=2 status=3 }
    fun startResponse(ip: String, port: Int, status: Int = 0): ByteArray = Proto.Writer()
        .string(1, ip)
        .intAlways(2, port)
        .intAlways(3, status)
        .toByteArray()
}
