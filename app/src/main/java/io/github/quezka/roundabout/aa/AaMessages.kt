package io.github.quezka.roundabout.aa

// AA protobuf messages on top of Proto (schemas from aasdk protobuf/aap_protobuf)
object AaMessages {

    // channel ids (aasdk ChannelId.hpp)
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
    object AudioStream { const val GUIDANCE = 1; const val SYSTEM = 2; const val MEDIA = 3 }
    object VideoRes { const val R800x480 = 1; const val R1280x720 = 2; const val R1920x1080 = 3 }
    object VideoFps { const val FPS_60 = 1; const val FPS_30 = 2 }
    object Display { const val MAIN = 0; const val CLUSTER = 1; const val AUXILIARY = 2 }
    object Touch { const val CAPACITIVE = 1 }
    object Sensor { const val LOCATION = 1; const val SPEED = 3; const val NIGHT_MODE = 10; const val DRIVING_STATUS = 13 }
    object Status { const val SUCCESS = 0 }

    // advertised video geometry ; also the touch coordinate space
    object Video {
        const val W = 1280
        const val H = 720
        const val RES = VideoRes.R1280x720
        const val FPS = VideoFps.FPS_30
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

    // ServiceDiscoveryResponse: video + media audio + input + sensor + headunit_info
    fun serviceDiscoveryResponse(displayName: String = "Roundabout"): ByteArray {
        val w = Proto.Writer()
        w.message(1, service(Ch.MEDIA_SINK_VIDEO, video = true))
        w.message(1, service(Ch.MEDIA_SINK_MEDIA_AUDIO, audioStream = AudioStream.MEDIA))
        w.message(1, service(Ch.INPUT_SOURCE, input = true))
        w.message(1, service(Ch.SENSOR, sensor = true))
        w.message(17, headUnitInfo(displayName))
        return w.toByteArray()
    }

    // Service { required int32 id = 1 ; sensor_source=2 media_sink=3 input_source=4 }
    private fun service(
        id: Int,
        video: Boolean = false,
        audioStream: Int = -1,
        input: Boolean = false,
        sensor: Boolean = false,
    ): ByteArray {
        val w = Proto.Writer().intAlways(1, id)
        when {
            video -> w.message(3, mediaSink(video = true))
            audioStream >= 0 -> w.message(3, mediaSink(audioStream = audioStream))
            input -> w.message(4, inputSource())
            sensor -> w.message(2, sensorSource())
        }
        return w.toByteArray()
    }

    // MediaSinkService { available_type=1 audio_type=2 audio_configs=3 video_configs=4 display_type=7 }
    private fun mediaSink(video: Boolean = false, audioStream: Int = -1): ByteArray {
        val w = Proto.Writer()
        if (video) {
            w.intAlways(1, Codec.VIDEO_H264_BP)
            // VideoConfiguration { codec_resolution=1 frame_rate=2 video_codec_type=10 }
            val vc = Proto.Writer()
                .int(1, Video.RES)
                .int(2, Video.FPS)
                .int(10, Codec.VIDEO_H264_BP)
                .toByteArray()
            w.message(4, vc)
            w.int(7, Display.MAIN)
        } else {
            w.intAlways(1, Codec.AUDIO_PCM)
            w.int(2, audioStream)
            // AudioConfiguration { sampling_rate=1 number_of_bits=2 number_of_channels=3 }
            val ac = Proto.Writer()
                .intAlways(1, 48000)
                .intAlways(2, 16)
                .intAlways(3, 2)
                .toByteArray()
            w.message(3, ac)
        }
        return w.toByteArray()
    }

    // InputSourceService { TouchScreen touchscreen = 2 { width=1 height=2 type=3 } }
    private fun inputSource(): ByteArray {
        val ts = Proto.Writer()
            .intAlways(1, Video.W)
            .intAlways(2, Video.H)
            .int(3, Touch.CAPACITIVE)
            .toByteArray()
        return Proto.Writer().message(2, ts).toByteArray()
    }

    // SensorSourceService { repeated Sensor sensors = 1 ; Sensor { sensor_type = 1 } }
    private fun sensorSource(): ByteArray {
        val w = Proto.Writer()
        for (s in intArrayOf(Sensor.LOCATION, Sensor.SPEED, Sensor.NIGHT_MODE, Sensor.DRIVING_STATUS)) {
            w.message(1, Proto.Writer().intAlways(1, s).toByteArray())
        }
        return w.toByteArray()
    }

    // HeadUnitInfo { make=1 model=2 head_unit_make=5 head_unit_model=6 sw_version=8 }
    private fun headUnitInfo(name: String): ByteArray = Proto.Writer()
        .string(1, name)
        .string(2, name)
        .string(5, name)
        .string(6, name)
        .string(8, "1.0")
        .toByteArray()
}
