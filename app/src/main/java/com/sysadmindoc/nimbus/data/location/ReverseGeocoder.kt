package com.sysadmindoc.nimbus.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "ReverseGeocoder"
// The platform geocoder can stall when its backend is unreachable; the async
// (API 33+) callback has no built-in timeout, so bound it ourselves.
private const val GEOCODE_TIMEOUT_MS = 5_000L

data class ReverseGeoResult(
    val name: String,
    val region: String,
    val country: String,
)

/**
 * Names the place after the first of town, county and state that isn't blank,
 * so an empty town name falls through exactly like a missing one. Null when
 * none is usable, and the caller then uses Open-Meteo's name. Both API branches
 * below and the map picker read addresses through this one mapping.
 */
internal fun Address.toReverseGeoResult(): ReverseGeoResult? {
    val name = listOf(locality, subAdminArea, adminArea).firstOrNull { !it.isNullOrBlank() } ?: return null
    return ReverseGeoResult(
        name = name,
        region = adminArea ?: "",
        country = countryName ?: "",
    )
}

@Singleton
class ReverseGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun resolve(latitude: Double, longitude: Double): ReverseGeoResult? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resolveAsync(geocoder, latitude, longitude)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.toReverseGeoResult()
                }
            } catch (e: IOException) {
                // Geocoder backend unavailable/offline — distinct from "no result".
                Log.w(TAG, "reverse geocode backend unavailable", e)
                null
            } catch (e: Exception) {
                Log.w(TAG, "reverse geocode failed", e)
                null
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun resolveAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): ReverseGeoResult? = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (!cont.isActive) return
                        cont.resume(addresses.firstOrNull()?.toReverseGeoResult())
                    }

                    override fun onError(errorMessage: String?) {
                        // Without this the continuation would never resume and the
                        // coroutine would hang until the surrounding timeout fires.
                        Log.w(TAG, "reverse geocode error: $errorMessage")
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }
    }
}
