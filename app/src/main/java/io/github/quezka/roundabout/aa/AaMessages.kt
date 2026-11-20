package io.github.quezka.roundabout.aa

// AA protobuf messages (schema from the Wifi.proto of a working hu)
object AaMessages {

    // channel ids
    object Ch {
        const val CONTROL = 0
        const val SENSOR = 1
        const val MEDIA_SINK_VIDEO = 3
        const val MEDIA_SINK_MEDIA_AUDIO = 4
        const val MEDIA_SINK_GUIDANCE_AUDIO = 5
        const val MEDIA_SINK_SYSTEM_AUDIO = 6
        const val INPUT_SOURCE = 8
    }

    object Codec { const val AUDIO_PCM = 1; const val VIDEO_H264_BP = 3 }
    // AVStreamType: AUDIO=1 VIDEO=3
    object StreamType { const val AUDIO = 1; const val VIDEO = 3 }
    // AudioType: SPEECH=1 SYSTEM=2 MEDIA=3
    object AudioStream { const val GUIDANCE = 1; const val SYSTEM = 2; const val MEDIA = 3 }
    // VideoResolution: 480p=1 720p=2 1080p=3
    object VideoRes { const val R800x480 = 1; const val R1280x720 = 2; const val R1920x1080 = 3 }
    // VideoFPS: 30=1 60=2
    object VideoFps { const val FPS_30 = 1; const val FPS_60 = 2 }
    object Touch { const val CAPACITIVE = 1 }
    object Sensor { const val LOCATION = 1; const val SPEED = 3; const val NIGHT_MODE = 10; const val DRIVING_STATUS = 13 }
    object Status { const val SUCCESS = 0 }

    // advertised video geometry ; also the touch coordinate space
    object Video {
        const val W = 1280
        const val H = 720
        const val RES = VideoRes.R1280x720
        const val FPS = VideoFps.FPS_30
        const val DPI = 160
    }

    // AuthResponse { required int32 status = 1 }
    fun authStatus(payload: ByteArray): Int {
        val r = Proto.Reader(payload)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == 1 && w == Proto.Wire.VARINT) return r.varint().toInt()
            r.skip(w)
        }
        return 0
    }

    // ChannelOpenRequest { required sint32 priority = 1 ; required int32 service_id = 2 }
    data class ChannelOpen(val priority: Int, val serviceId: Int)
    fun channelOpenRequest(payload: ByteArray): ChannelOpen {
        var prio = 0; var sid = 0
        val r = Proto.Reader(payload)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            when {
                f == 1 && w == Proto.Wire.VARINT -> prio = Proto.unzigzag(r.varint())
                f == 2 && w == Proto.Wire.VARINT -> sid = r.varint().toInt()
                else -> r.skip(w)
            }
        }
        return ChannelOpen(prio, sid)
    }

    // ChannelOpenResponse { required MessageStatus status = 1 }
    fun channelOpenResponse(status: Int): ByteArray = Proto.Writer().intAlways(1, status).toByteArray()

    // ServiceDiscoveryResponse ; fields 2..11 are required by the phone
    fun serviceDiscoveryResponse(displayName: String = "Roundabout"): ByteArray {
        val w = Proto.Writer()
        w.message(1, videoChannel())
        w.message(1, mediaAudioChannel())
        w.message(1, inputChannel())
        w.message(1, sensorChannel())
        w.string(2, displayName)          // head_unit_name
        w.string(3, "Universal")          // car_model
        w.string(4, "2026")               // car_year
        w.string(5, "RB-01")              // car_serial
        w.boolAlways(6, true)             // left_hand_drive_vehicle
        w.string(7, "quezka")             // headunit_manufacturer
        w.string(8, "Roundabout")         // headunit_model
        w.string(9, "1")                  // sw_build
        w.string(10, "1.0")               // sw_version
        w.boolAlways(11, false)           // can_play_native_media_during_vr
        return w.toByteArray()
    }

    // ChannelDescriptor { channel_id=1 sensor=2 av=3 input=4 }
    private fun videoChannel(): ByteArray {
        val cfg = Proto.Writer()
            .intAlways(1, Video.RES)      // video_resolution
            .intAlways(2, Video.FPS)      // video_fps
            .intAlways(3, 0)              // margin_width
            .intAlways(4, 0)              // margin_height
            .intAlways(5, Video.DPI)      // dpi
            .toByteArray()
        val av = Proto.Writer()
            .intAlways(1, StreamType.VIDEO)
            .message(4, cfg)              // video_configs
            .intAlways(5, 1)              // available_while_in_call
            .toByteArray()
        return Proto.Writer().intAlways(1, Ch.MEDIA_SINK_VIDEO).message(3, av).toByteArray()
    }

    private fun mediaAudioChannel(): ByteArray {
        val cfg = Proto.Writer()
            .intAlways(1, 48000)          // sample_rate
            .intAlways(2, 16)             // bit_depth
            .intAlways(3, 2)              // channel_count
            .toByteArray()
        val av = Proto.Writer()
            .intAlways(1, StreamType.AUDIO)
            .intAlways(2, AudioStream.MEDIA)
            .message(3, cfg)              // audio_configs
            .toByteArray()
        return Proto.Writer().intAlways(1, Ch.MEDIA_SINK_MEDIA_AUDIO).message(3, av).toByteArray()
    }

    private fun inputChannel(): ByteArray {
        val touch = Proto.Writer().intAlways(1, Video.W).intAlways(2, Video.H).toByteArray()
        val ic = Proto.Writer().message(2, touch).toByteArray()
        return Proto.Writer().intAlways(1, Ch.INPUT_SOURCE).message(4, ic).toByteArray()
    }

    private fun sensorChannel(): ByteArray {
        val sc = Proto.Writer()
        for (t in intArrayOf(Sensor.LOCATION, Sensor.SPEED, Sensor.NIGHT_MODE, Sensor.DRIVING_STATUS)) {
            sc.message(1, Proto.Writer().intAlways(1, t).toByteArray())
        }
        return Proto.Writer().intAlways(1, Ch.SENSOR).message(2, sc.toByteArray()).toByteArray()
    }
}
