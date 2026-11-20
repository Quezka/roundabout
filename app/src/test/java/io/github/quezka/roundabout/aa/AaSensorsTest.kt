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

    @Test fun locationBatchCarriesPositionSpeedAndBearing() {
        val batch = AaSensors.locationBatch(
            latE7 = 452_123_456,
            lonE7 = 91_234_567,
            accuracyM = 12.5f,
            altitudeM = 250.0,
            speedMps = 22.5f,
            bearingDeg = 90f,
            speedKmh = 81,
        )
        var loc: ByteArray? = null
        var spd: ByteArray? = null
        val r = Proto.Reader(batch)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            when {
                f == 1 && w == Proto.Wire.LEN -> loc = r.bytes()
                f == 3 && w == Proto.Wire.LEN -> spd = r.bytes()
                else -> r.skip(w)
            }
        }
        assertEquals(452_123_456, Proto.varintField(loc!!, 2).toInt())
        assertEquals(91_234_567, Proto.varintField(loc, 3).toInt())
        assertEquals(12_500, Proto.varintField(loc, 4).toInt())
        assertEquals(25_000, Proto.varintField(loc, 5).toInt())
        assertEquals(22_500, Proto.varintField(loc, 6).toInt())
        assertEquals(90_000_000, Proto.varintField(loc, 7).toInt())
        assertEquals(81_000, Proto.varintField(spd!!, 1).toInt())
    }

    @Test fun negativeCoordinatesRoundTrip() {
        val batch = AaSensors.locationBatch(latE7 = -338_654_321, lonE7 = -70_123_456)
        var loc: ByteArray? = null
        val r = Proto.Reader(batch)
        while (!r.eof()) {
            val t = r.tag(); val f = Proto.field(t); val w = Proto.wire(t)
            if (f == 1 && w == Proto.Wire.LEN) loc = r.bytes() else r.skip(w)
        }
        assertEquals(-338_654_321, Proto.varintField(loc!!, 2).toInt())
        assertEquals(-70_123_456, Proto.varintField(loc, 3).toInt())
    }

    @Test fun sensorChannelAnswersRequestAndPushesValue() {
        val sent = ArrayList<Pair<Int, ByteArray>>()
        val ch = SensorChannel({ t, b -> sent.add(t to b) })
        val req = AaAv.message(AaSensors.Msg.REQUEST, Proto.Writer().intAlways(1, AaSensors.DRIVING_STATUS).toByteArray())
        ch.onMessage(req)
        assertEquals(listOf(AaSensors.Msg.RESPONSE, AaSensors.Msg.BATCH), sent.map { it.first })
    }
}
