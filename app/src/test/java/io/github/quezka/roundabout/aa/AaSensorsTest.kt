package io.github.quezka.roundabout.aa

import org.junit.Assert.assertEquals
import org.junit.Test

class AaSensorsTest {
    @Test fun requestTypeDecode() {
        val body = Proto.Writer().intAlways(1, AaSensors.DRIVING_STATUS).intAlways(2, 100).toByteArray()
        assertEquals(AaSensors.DRIVING_STATUS, AaSensors.requestType(body))
    }

    @Test fun drivingStatusBatchShape() {
        val batch = AaSensors.drivingStatusBatch(AaSensors.DriveStatus.UNRESTRICTED)
        // SensorBatch.driving_status_data (13) -> DrivingStatusData.status (1) = 0
        val r = Proto.Reader(batch)
        var inner: ByteArray? = null
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == 13 && w == Proto.Wire.LEN) inner = r.bytes() else r.skip(w)
        }
        assertEquals(0, Proto.varintField(inner!!, 1).toInt())
    }

    @Test fun nightModeBatchShape() {
        val on = AaSensors.nightModeBatch(true)
        val r = Proto.Reader(on)
        var inner: ByteArray? = null
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == 10 && w == Proto.Wire.LEN) inner = r.bytes() else r.skip(w)
        }
        assertEquals(1, Proto.varintField(inner!!, 1).toInt())
    }

    @Test fun sensorChannelAnswersRequestAndPushesValue() {
        val sent = ArrayList<Pair<Int, ByteArray>>()
        val ch = SensorChannel({ t, b -> sent.add(t to b) })
        val req = AaAv.message(AaSensors.Msg.REQUEST, Proto.Writer().intAlways(1, AaSensors.DRIVING_STATUS).toByteArray())
        ch.onMessage(req)
        assertEquals(listOf(AaSensors.Msg.RESPONSE, AaSensors.Msg.BATCH), sent.map { it.first })
    }
}
