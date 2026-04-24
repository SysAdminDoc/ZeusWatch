package com.sysadmindoc.nimbus.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON response models for MET Norway LocationForecast 2.0 `/complete`.
 *
 * Top-level envelope is a GeoJSON Feature; only the `properties` body is
 * weather data. Every numeric field is nullable because MET does not
 * guarantee any field in every timeseries entry — `next_6_hours` and
 * `next_12_hours` blocks drop off as the forecast horizon shortens,
 * percentiles only appear at extended horizons, gusts are sometimes
 * absent, etc.
 *
 * Units are metric as documented at
 * <https://api.met.no/weatherapi/locationforecast/2.0/documentation>:
 *   air_temperature                  °C
 *   relative_humidity                %
 *   air_pressure_at_sea_level        hPa
 *   wind_speed / wind_speed_of_gust  m/s (convert to km/h in adapter)
 *   wind_from_direction              degrees (compass, from)
 *   precipitation_amount             mm
 *   probability_of_precipitation     %
 *   ultraviolet_index_clear_sky      UV index
 *   cloud_area_fraction              %
 *   fog_area_fraction                %
 */
@Serializable
data class MetNorwayResponse(
    val type: String? = null,
    val properties: MetProperties? = null,
)

@Serializable
data class MetProperties(
    val meta: MetMeta? = null,
    val timeseries: List<MetTimeseriesEntry> = emptyList(),
)

@Serializable
data class MetMeta(
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MetTimeseriesEntry(
    val time: String,
    val data: MetEntryData? = null,
)

@Serializable
data class MetEntryData(
    val instant: MetInstant? = null,
    @SerialName("next_1_hours") val next1Hours: MetPeriod? = null,
    @SerialName("next_6_hours") val next6Hours: MetPeriod? = null,
    @SerialName("next_12_hours") val next12Hours: MetPeriod? = null,
)

@Serializable
data class MetInstant(
    val details: MetInstantDetails? = null,
)

@Serializable
data class MetInstantDetails(
    @SerialName("air_temperature") val airTemperature: Double? = null,
    @SerialName("air_temperature_percentile_10") val airTemperaturePercentile10: Double? = null,
    @SerialName("air_temperature_percentile_90") val airTemperaturePercentile90: Double? = null,
    @SerialName("air_pressure_at_sea_level") val airPressureAtSeaLevel: Double? = null,
    @SerialName("cloud_area_fraction") val cloudAreaFraction: Double? = null,
    @SerialName("cloud_area_fraction_high") val cloudAreaFractionHigh: Double? = null,
    @SerialName("cloud_area_fraction_low") val cloudAreaFractionLow: Double? = null,
    @SerialName("cloud_area_fraction_medium") val cloudAreaFractionMedium: Double? = null,
    @SerialName("dew_point_temperature") val dewPointTemperature: Double? = null,
    @SerialName("fog_area_fraction") val fogAreaFraction: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Double? = null,
    @SerialName("ultraviolet_index_clear_sky") val ultravioletIndexClearSky: Double? = null,
    @SerialName("wind_from_direction") val windFromDirection: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_speed_of_gust") val windSpeedOfGust: Double? = null,
)

@Serializable
data class MetPeriod(
    val summary: MetSummary? = null,
    val details: MetPeriodDetails? = null,
)

@Serializable
data class MetSummary(
    @SerialName("symbol_code") val symbolCode: String? = null,
)

@Serializable
data class MetPeriodDetails(
    @SerialName("air_temperature_max") val airTemperatureMax: Double? = null,
    @SerialName("air_temperature_min") val airTemperatureMin: Double? = null,
    @SerialName("precipitation_amount") val precipitationAmount: Double? = null,
    @SerialName("precipitation_amount_max") val precipitationAmountMax: Double? = null,
    @SerialName("precipitation_amount_min") val precipitationAmountMin: Double? = null,
    @SerialName("probability_of_precipitation") val probabilityOfPrecipitation: Double? = null,
    @SerialName("probability_of_thunder") val probabilityOfThunder: Double? = null,
    @SerialName("ultraviolet_index_clear_sky_max") val ultravioletIndexClearSkyMax: Double? = null,
)
