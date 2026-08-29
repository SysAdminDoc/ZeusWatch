package com.sysadmindoc.nimbus.wear.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.sysadmindoc.nimbus.wear.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
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

    /**
     * Resolves the watch's location, or `null` when nothing is known.
     *
     * A null result means "we have no idea where the user is" — callers must
     * render a no-location state rather than fetching a forecast. The watch
     * used to fall back to the geographic center of the contiguous US, which
     * rendered Kansas weather as if it were the user's own.
     */
    suspend fun getLocation(): LocationResult? {
        if (hasPermission()) {
            try {
                val loc = fetchLastLocation()
                if (loc != null) {
                    cache(loc.latitude, loc.longitude)
                    val name = reverseGeocode(loc.latitude, loc.longitude)
                        ?: context.getString(R.string.wear_current_location)
                    prefs.edit().putString("name", name).apply()
                    return LocationResult(loc.latitude, loc.longitude, name)
                }
            } catch (cancelled: CancellationException) {
                // Never mask cancellation as "use cached location" — the
                // caller's structured concurrency must see the cancel.
                throw cancelled
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
            .addOnSuccessListener { cont.resumeLocationIfActive(it) }
            .addOnFailureListener { cont.resumeLocationIfActive(null) }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addrs = geocoder.getFromLocation(lat, lon, 1)
                addrs?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    private fun cache(lat: Double, lon: Double) {
        // Store as bit-preserving Long → Double round-trips cleanly. Float storage
        // loses ~11 meters of precision at the equator and breaks the "was this
        // ever saved?" check below because Float→Double→Double equality fails.
        prefs.edit()
            .putLong("lat_bits", java.lang.Double.doubleToRawLongBits(lat))
            .putLong("lon_bits", java.lang.Double.doubleToRawLongBits(lon))
            .remove("lat")
            .remove("lon")
            .apply()
    }

    private fun cached(): LocationResult? {
        val hasBits = prefs.contains("lat_bits") && prefs.contains("lon_bits")
        if (hasBits) {
            return LocationResult(
                lat = java.lang.Double.longBitsToDouble(prefs.getLong("lat_bits", 0L)),
                lon = java.lang.Double.longBitsToDouble(prefs.getLong("lon_bits", 0L)),
                name = savedName(),
            )
        }
        // Legacy Float path — preserved so existing installs don't lose their
        // cached location when they first run the updated build.
        if (prefs.contains("lat") && prefs.contains("lon")) {
            return LocationResult(
                lat = prefs.getFloat("lat", 0f).toDouble(),
                lon = prefs.getFloat("lon", 0f).toDouble(),
                name = savedName(),
            )
        }
        return null
    }

    private fun savedName(): String =
        prefs.getString("name", null) ?: context.getString(R.string.wear_saved_location)

    data class LocationResult(val lat: Double, val lon: Double, val name: String)
}

private fun CancellableContinuation<Location?>.resumeLocationIfActive(location: Location?) {
    if (!isActive) return
    try {
        resume(location)
    } catch (_: IllegalStateException) {
        // A Play services Task callback can arrive after coroutine
        // cancellation or after another listener already completed it.
        // The caller will fall back to cached coordinates.
    }
}
