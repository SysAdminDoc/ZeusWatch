package com.sysadmindoc.nimbus.data.repository

/**
 * Where any pollen forecast exists. Open-Meteo's pollen comes only from the
 * CAMS European air quality forecast, whose domain is 30° N to 72° N and
 * 25° W to 45° E (Copernicus ADS, "cams-europe-air-quality-forecasts").
 * Outside it every pollen value is null all year, so a missing reading there
 * is permanent, not a failed fetch. Checked 2026-11-15: New York returns
 * null, the domain's north-east corner returns 0.0 out of season. At the
 * edges Open-Meteo goes by the coordinate asked for, not the grid point it
 * snaps to: 30.0° N, 25.0° W and 45.0° E have data, while 29.97° N, 25.04° W,
 * 45.04° E and exactly 72.0° N don't.
 */
object PollenCoverage {
    fun isCovered(latitude: Double, longitude: Double): Boolean =
        latitude >= 30.0 && latitude < 72.0 && longitude in -25.0..45.0
}
