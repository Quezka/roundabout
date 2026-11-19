package io.github.quezka.roundabout.data

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import io.github.quezka.roundabout.domain.SpeedSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GpsSpeedSource(private val context: Context) : SpeedSource {

    override fun speed(): Flow<Float?> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // ! override all 4 fn <- api < 30 interface has no default -> AbstractMethodError
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(if (location.hasSpeed()) location.speed else null)
            }
            @Deprecated("old api")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { trySend(null) }
        }
        trySend(null)
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
        } catch (e: IllegalArgumentException) {
            // no gps provider
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}
