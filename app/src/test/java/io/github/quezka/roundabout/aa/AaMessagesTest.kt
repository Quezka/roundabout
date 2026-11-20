package io.github.quezka.roundabout.aa

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AaMessagesTest {
    @Test fun serviceDiscoveryListsChannels() {
        val bytes = AaMessages.serviceDiscoveryResponse()
        val r = Proto.Reader(bytes)
        val ids = ArrayList<Int>()
        val cfg = ArrayList<Int>()
        val required = HashSet<Int>()
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            when {
                f == 1 && w == Proto.Wire.LEN -> {
                    val svc = Proto.Reader(r.bytes())
                    var id = -1; var field = -1
                    while (!svc.eof()) {
                        val st = svc.tag(); val sf = Proto.field(st); val sw = Proto.wire(st)
                        if (sf == 1 && sw == Proto.Wire.VARINT) id = svc.varint().toInt()
                        else { field = sf; svc.skip(sw) }
                    }
                    ids.add(id); cfg.add(field)
                }
                else -> { required.add(f); r.skip(w) }
            }
        }
        assertEquals(
            listOf(AaMessages.Ch.MEDIA_SINK_VIDEO, AaMessages.Ch.MEDIA_SINK_MEDIA_AUDIO, AaMessages.Ch.INPUT_SOURCE, AaMessages.Ch.SENSOR),
            ids,
        )
        assertEquals(listOf(3, 3, 4, 2), cfg) // av_channel, av_channel, input_channel, sensor_channel
        // every field the phone marks required must be present
        assertTrue(required.containsAll(setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11)))
    }

    @Test fun channelOpenDecode() {
        val p = Proto.Writer().sint(1, -5).int(2, 4).toByteArray()
        val req = AaMessages.channelOpenRequest(p)
        assertEquals(-5, req.priority)
        assertEquals(4, req.serviceId)
    }

    @Test fun channelOpenResponseStatus() {
        assertArrayEquals(byteArrayOf(8, 0), AaMessages.channelOpenResponse(0)) // tag(1,varint)=8, 0
        assertArrayEquals(byteArrayOf(8, 2), AaMessages.channelOpenResponse(2))
    }

    @Test fun authStatusDecode() {
        assertEquals(0, AaMessages.authStatus(ByteArray(0)))
        assertEquals(-3, AaMessages.authStatus(Proto.Writer().intAlways(1, -3).toByteArray()))
        assertEquals(0, AaMessages.authStatus(Proto.Writer().intAlways(1, 0).toByteArray()))
    }
}
