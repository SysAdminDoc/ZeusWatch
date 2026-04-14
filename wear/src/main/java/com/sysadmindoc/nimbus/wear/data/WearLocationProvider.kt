package com.sysadmindoc.nimbus.wear.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class WearLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationClient: FusedLocationProviderClient,
) {
    private val prefs = context.getSharedPreferences("wear_location", Context.MODE_PRIVATE)

    suspend fun getLocation(): LocationResult {
        if (hasPermission()) {
            try {
                val loc = fetchLastLocation()
                if (loc != null) {
                    cache(loc.latitude, loc.longitude)
                    val name = reverseGeocode(loc.latitude, loc.longitude) ?: "Current Location"
                    prefs.edit().putString("name", name).apply()
                    return LocationResult(loc.latitude, loc.longitude, name)
                }
            } catch (_: Exception) {
                // Fall through to cached
            }
        }
        return cached()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun fetchLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        locationClient.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addrs = geocoder.getFromLocation(lat, lon, 1)
                addrs?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            } catch (_: Exception) {
                null
            }
        }

    private fun cache(lat: Double, lon: Double) {
        prefs.edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat())
            .apply()
    }

    private fun cached(): LocationResult {
        val lat = prefs.getFloat("lat", DEFAULT_LAT.toFloat()).toDouble()
        val lon = prefs.getFloat("lon", DEFAULT_LON.toFloat()).toDouble()
        val name = prefs.getString("name", null)
            ?: if (lat == DEFAULT_LAT && lon == DEFAULT_LON) "Central US" else "Saved Location"
        return LocationResult(lat, lon, name)
    }

    data class LocationResult(val lat: Double, val lon: Double, val name: String)

    companion object {
        const val DEFAULT_LAT = 39.8
        const val DEFAULT_LON = -98.5
    }
}
