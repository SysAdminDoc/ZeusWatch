package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.CommunityReport
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
private const val EARTH_RADIUS_KM = 6_371.0088
private const val KM_PER_LAT_DEGREE = 111.0
private const val MIN_LON = -180.0
private const val MAX_LON = 180.0

internal data class GeohashQueryBound(
    val start: String,
    val end: String,
)

internal object CommunityReportGeo {
    fun geohash(latitude: Double, longitude: Double, precision: Int = precisionForRadius(50.0)): String {
        val lat = latitude.coerceIn(-90.0, 90.0)
        val lon = normalizeLongitude(longitude)
        var latRange = -90.0..90.0
        var lonRange = -180.0..180.0
        val hash = StringBuilder(precision)
        var bit = 0
        var value = 0
        var evenBit = true

        while (hash.length < precision) {
            if (evenBit) {
                val mid = (lonRange.start + lonRange.endInclusive) / 2.0
                if (lon >= mid) {
                    value = value shl 1 or 1
                    lonRange = mid..lonRange.endInclusive
                } else {
                    value = value shl 1
                    lonRange = lonRange.start..mid
                }
            } else {
                val mid = (latRange.start + latRange.endInclusive) / 2.0
                if (lat >= mid) {
                    value = value shl 1 or 1
                    latRange = mid..latRange.endInclusive
                } else {
                    value = value shl 1
                    latRange = latRange.start..mid
                }
            }
            evenBit = !evenBit
            bit++

            if (bit == 5) {
                hash.append(BASE32[value])
                bit = 0
                value = 0
            }
        }

        return hash.toString()
    }

    fun queryBounds(latitude: Double, longitude: Double, radiusKm: Double): List<GeohashQueryBound> {
        val precision = precisionForRadius(radiusKm)
        return prefixesFor(latitude, longitude, radiusKm, precision).map { prefix ->
            GeohashQueryBound(start = prefix, end = "$prefix~")
        }
    }

    fun isNearby(report: CommunityReport, latitude: Double, longitude: Double, radiusKm: Double): Boolean =
        distanceKm(latitude, longitude, report.latitude, report.longitude) <= radiusKm

    fun sortNearby(
        reports: Iterable<CommunityReport>,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        maxResults: Int,
    ): List<CommunityReport> =
        reports
            .filter { isNearby(it, latitude, longitude, radiusKm) }
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
            .take(maxResults)

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(normalizedLongitudeDelta(lon2 - lon1))
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2).pow(2) + cos(radLat1) * cos(radLat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
    }

    private fun prefixesFor(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        precision: Int,
    ): List<String> {
        val lat = latitude.coerceIn(-90.0, 90.0)
        val lon = normalizeLongitude(longitude)
        val latDelta = radiusKm / KM_PER_LAT_DEGREE
        val cosLat = cos(Math.toRadians(lat)).let { if (it < 0.01) 0.01 else it }
        val lonDelta = radiusKm / (KM_PER_LAT_DEGREE * cosLat)
        val latMin = (lat - latDelta).coerceAtLeast(-90.0)
        val latMax = (lat + latDelta).coerceAtMost(90.0)
        val lonMin = lon - lonDelta
        val lonMax = lon + lonDelta
        val cell = cellDegrees(precision)
        val lonRanges = longitudeRanges(lonMin, lonMax)
        val prefixes = linkedSetOf<String>()

        for (latSample in gridSamples(latMin, latMax, cell.latitudeDegrees)) {
            for (range in lonRanges) {
                for (lonSample in gridSamples(range.start, range.endInclusive, cell.longitudeDegrees)) {
                    prefixes += geohash(latSample, lonSample, precision)
                }
            }
        }

        return prefixes.toList()
    }

    private fun precisionForRadius(radiusKm: Double): Int =
        when {
            radiusKm >= 80.0 -> 3
            radiusKm >= 20.0 -> 4
            radiusKm >= 5.0 -> 5
            else -> 6
        }

    private fun cellDegrees(precision: Int): GeohashCellDegrees {
        val totalBits = precision * 5
        val lonBits = ceil(totalBits / 2.0).toInt()
        val latBits = floor(totalBits / 2.0).toInt()
        return GeohashCellDegrees(
            latitudeDegrees = 180.0 / 2.0.pow(latBits),
            longitudeDegrees = 360.0 / 2.0.pow(lonBits),
        )
    }

    private fun gridSamples(minValue: Double, maxValue: Double, step: Double): Sequence<Double> = sequence {
        val safeStep = step.coerceAtLeast(0.000001)
        var sample = floor(minValue / safeStep) * safeStep
        while (sample <= maxValue + safeStep) {
            yield(sample.coerceIn(minValue, maxValue))
            sample += safeStep
        }
        yield(maxValue)
    }

    private fun longitudeRanges(minLongitude: Double, maxLongitude: Double): List<ClosedFloatingPointRange<Double>> =
        when {
            maxLongitude - minLongitude >= 360.0 -> listOf(MIN_LON..MAX_LON)
            minLongitude < MIN_LON -> listOf(
                (minLongitude + 360.0)..MAX_LON,
                MIN_LON..maxLongitude,
            )
            maxLongitude > MAX_LON -> listOf(
                minLongitude..MAX_LON,
                MIN_LON..(maxLongitude - 360.0),
            )
            else -> listOf(minLongitude..maxLongitude)
        }

    private fun normalizeLongitude(longitude: Double): Double {
        val normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return if (normalized == -180.0) 180.0 else normalized
    }

    private fun normalizedLongitudeDelta(delta: Double): Double =
        ((delta + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private data class GeohashCellDegrees(
        val latitudeDegrees: Double,
        val longitudeDegrees: Double,
    )
}
