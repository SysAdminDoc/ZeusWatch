package com.sysadmindoc.nimbus.data.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ReverseGeoResult(
    val name: String,
    val region: String,
    val country: String,
)

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
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val addr = addresses?.firstOrNull() ?: return@withContext null
                    ReverseGeoResult(
                        name = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: return@withContext null,
                        region = addr.adminArea ?: "",
                        country = addr.countryName ?: "",
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun resolveAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
    ): ReverseGeoResult? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                val addr = addresses.firstOrNull()
                if (addr != null) {
                    cont.resume(
                        ReverseGeoResult(
                            name = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "",
                            region = addr.adminArea ?: "",
                            country = addr.countryName ?: "",
                        )
                    )
                } else {
                    cont.resume(null)
                }
            }
        } else {
            cont.resume(null)
        }
    }
}
