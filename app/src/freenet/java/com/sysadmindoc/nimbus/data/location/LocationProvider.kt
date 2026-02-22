package com.sysadmindoc.nimbus.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocationProvider"

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
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withContext Result.failure(Exception("Location service not available"))

            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            )
            val location = providers.firstNotNullOfOrNull { provider ->
                try {
                    @Suppress("MissingPermission")
                    lm.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
            }

            if (location != null) {
                Log.d(TAG, "getCurrentLocation: ${location.latitude}, ${location.longitude}")
                Result.success(location)
            } else {
                Log.w(TAG, "getCurrentLocation: no location available")
                Result.failure(Exception("Unable to determine location. Make sure GPS is enabled."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentLocation: error", e)
            Result.failure(e)
        }
    }
}
