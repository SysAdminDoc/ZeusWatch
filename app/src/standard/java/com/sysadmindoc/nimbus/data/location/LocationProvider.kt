package com.sysadmindoc.nimbus.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "LocationProvider"

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

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
            // 1) Try cached/last known location first (instant)
            val lastKnown = getLastKnownFusedLocation()
            if (lastKnown != null) {
                Log.d(TAG, "getCurrentLocation: using lastKnown ${lastKnown.latitude}, ${lastKnown.longitude}")
                return@withContext Result.success(lastKnown)
            }

            // 2) Try fresh fused location with timeout
            Log.d(TAG, "getCurrentLocation: requesting fresh fused location...")
            val fresh = withTimeoutOrNull(10_000L) { getFusedLocation() }
            if (fresh != null) {
                Log.d(TAG, "getCurrentLocation: got fresh fused ${fresh.latitude}, ${fresh.longitude}")
                return@withContext Result.success(fresh)
            }

            // 3) Fallback to Android LocationManager
            Log.d(TAG, "getCurrentLocation: fused timed out, trying LocationManager...")
            val fallback = getLocationManagerLocation()
            if (fallback != null) {
                Log.d(TAG, "getCurrentLocation: got LocationManager ${fallback.latitude}, ${fallback.longitude}")
                return@withContext Result.success(fallback)
            }

            Log.w(TAG, "getCurrentLocation: all methods failed")
            Result.failure(Exception("Unable to determine location. Make sure GPS is enabled."))
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation: error", e)
            Result.failure(e)
        }
    }

    @SuppressWarnings("MissingPermission")
    private suspend fun getLastKnownFusedLocation(): Location? = try {
        suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    @SuppressWarnings("MissingPermission")
    private suspend fun getFusedLocation(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }

        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        } catch (_: SecurityException) {
            cont.resume(null)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun getLocationManagerLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers.firstNotNullOfOrNull { provider ->
            try {
                lm.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
        }
    }
}
