package io.github.quezka.roundabout.aa

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

// sensor values for AA come from the tablet itself: gps for position/speed,
// the light sensor for night mode. no vehicle bus, no phone-side data.
class TabletSensors(
    private val ctx: Context,
    private val onFix: (Fix) -> Unit,
    private val onNight: (Boolean) -> Unit,
) {
    data class Fix(
        val latE7: Int,
        val lonE7: Int,
        val accuracyM: Float,
        val altitudeM: Double,
        val speedMps: Float,
        val bearingDeg: Float,
        val hasSpeed: Boolean,
    )

    private var lm: LocationManager? = null
    private var sm: SensorManager? = null
    private var locListener: LocationListener? = null
    private var lightListener: SensorEventListener? = null

    fun start() {
        startLocation()
        startLight()
    }

    fun stop() {
        locListener?.let { l -> try { lm?.removeUpdates(l) } catch (e: Exception) { } }
        lightListener?.let { l -> try { sm?.unregisterListener(l) } catch (e: Exception) { } }
        locListener = null; lightListener = null
        lm = null; sm = null
    }

    private fun startLocation() {
        val m = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        lm = m
        // ! override all 4 fn <- api < 30 interface has no default -> AbstractMethodError
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) { onFix(fixOf(location)) }
            @Deprecated("old api")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        locListener = l
        try {
            if (m.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l, Looper.getMainLooper())
            }
            if (m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, l, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
        } catch (e: IllegalArgumentException) {
        }
    }

    private fun startLight() {
        val s = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm = s
        val light = s.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values.firstOrNull() ?: return
                onNight(lux < NIGHT_LUX)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        lightListener = l
        s.registerListener(l, light, SensorManager.SENSOR_DELAY_NORMAL)
    }

    companion object {
        const val NIGHT_LUX = 10f

        fun fixOf(loc: Location): Fix = Fix(
            latE7 = (loc.latitude * 1e7).toInt(),
            lonE7 = (loc.longitude * 1e7).toInt(),
            accuracyM = loc.accuracy,
            altitudeM = loc.altitude,
            speedMps = if (loc.hasSpeed()) loc.speed else 0f,
            bearingDeg = if (loc.hasBearing()) loc.bearing else 0f,
            hasSpeed = loc.hasSpeed(),
        )
    }
}
