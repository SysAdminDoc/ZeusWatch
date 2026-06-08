package com.sysadmindoc.nimbus.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "LocationProvider"
private const val LOCATION_REQUEST_TIMEOUT_MS = 6_000L
// Reject cached/last-known fixes older than this. A fix from hours ago (e.g.
// after a flight) would otherwise silently anchor every widget, alert, and
// forecast to the place the user has since left.
private const val MAX_CACHED_LOCATION_AGE_MS = 10 * 60 * 1000L

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    suspend fun getCurrentLocation(): Result<Location> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission) {
            Log.w(TAG, "getCurrentLocation: no permission")
            return@withContext Result.failure(SecurityException("Location permission not granted"))
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext Result.failure(Exception("Location service not available"))

            val enabledProviders = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
            ).filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }

            if (enabledProviders.isEmpty()) {
                Log.w(TAG, "getCurrentLocation: location services disabled")
                return@withContext Result.failure(
                    IllegalStateException("Location services are turned off.")
                )
            }

            val cachedLocation = getBestLastKnownLocation(
                locationManager = locationManager,
                providers = enabledProviders + LocationManager.PASSIVE_PROVIDER,
            )
            if (cachedLocation != null && cachedLocation.isRecent()) {
                Log.d(
                    TAG,
                    "getCurrentLocation: using fresh cached ${cachedLocation.latitude}, ${cachedLocation.longitude}"
                )
                return@withContext Result.success(cachedLocation)
            }

            for (provider in enabledProviders) {
                Log.d(TAG, "getCurrentLocation: requesting current fix from $provider")
                val freshLocation = getFreshLocation(locationManager, provider)
                if (freshLocation != null) {
                    Log.d(
                        TAG,
                        "getCurrentLocation: got fresh ${freshLocation.latitude}, ${freshLocation.longitude}"
                    )
                    return@withContext Result.success(freshLocation)
                }
            }

            // Last resort: a stale cached fix still beats no location at all.
            if (cachedLocation != null) {
                Log.d(TAG, "getCurrentLocation: falling back to stale cached")
                return@withContext Result.success(cachedLocation)
            }

            Log.w(TAG, "getCurrentLocation: no location available")
            Result.failure(
                IllegalStateException(
                    "Unable to determine location. Move outdoors or check location services."
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation: error", e)
            Result.failure(e)
        }
    }

    /**
     * Freshness guard for cached fixes, measured on the monotonic boot clock
     * ([Location.getElapsedRealtimeNanos]) so it's immune to wall-clock changes.
     */
    private fun Location.isRecent(): Boolean {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L
        return ageMs in 0..MAX_CACHED_LOCATION_AGE_MS
    }

    @Suppress("MissingPermission")
    private fun getBestLastKnownLocation(
        locationManager: LocationManager,
        providers: List<String>,
    ): Location? {
        return providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun getFreshLocation(
        locationManager: LocationManager,
        provider: String,
    ): Location? {
        return withTimeoutOrNull(LOCATION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }

                try {
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(context),
                    ) { location ->
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}
