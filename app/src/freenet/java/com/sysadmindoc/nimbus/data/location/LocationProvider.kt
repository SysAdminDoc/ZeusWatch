package com.sysadmindoc.nimbus.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
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
            if (cachedLocation != null) {
                Log.d(
                    TAG,
                    "getCurrentLocation: using cached ${cachedLocation.latitude}, ${cachedLocation.longitude}"
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
