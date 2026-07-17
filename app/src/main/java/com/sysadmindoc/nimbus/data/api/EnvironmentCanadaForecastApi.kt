package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Environment and Climate Change Canada (ECCC) forecast API.
 * Uses MSC GeoMet OGC API Features — `citypageweather-realtime`
 * collection on `api.weather.gc.ca`.
 *
 * No API key, Open Government Licence — Canada, JSON (GeoJSON) format.
 *
 * Schema documentation:
 *   https://eccc-msc.github.io/open-data/msc-data/citypageweather/readme_citypageweather_en/
 *
 * SCHEMA NOTE (2026-07): GeoMet rotated this collection from the flat
 * XML-derived shape (`forecastGroup.forecast[]`, `temperature_value`,
 * `period` as a plain string) to the bilingual dashboard shape: every
 * scalar arrives as an `{"en": …, "fr": …}` object, the forecast list
 * is `forecastGroup.forecasts[]`, and an `hourlyForecastGroup` with
 * real hourly data plus `riseSet` sunrise/sunset now exist. The models
 * below match the live payload (fixture: eccc_citypage_live.json).
 */
interface EnvironmentCanadaForecastApi {

    /**
     * Bounding-box query for nearby cities' weather products.
     *
     * @param bbox "<min_lon>,<min_lat>,<max_lon>,<max_lat>" per OGC spec.
     * @param limit number of features to return (default 10 gives us a
     *              handful of candidates to pick the closest from).
     */
    @GET("collections/citypageweather-realtime/items")
    suspend fun getCityWeather(
        @Query("bbox") bbox: String,
        @Query("lang") lang: String = "en",
        @Query("f") format: String = "json",
        @Query("limit") limit: Int = 10,
    ): EcccFeatureCollection

    companion object {
        const val BASE_URL = "https://api.weather.gc.ca/"
    }
}

@Serializable
data class EcccFeatureCollection(
    val type: String? = null,
    val features: List<EcccFeature> = emptyList(),
)

@Serializable
data class EcccFeature(
    val id: String? = null,
    val type: String? = null,
    val geometry: EcccGeometry? = null,
    val properties: EcccProperties? = null,
)

@Serializable
data class EcccGeometry(
    val type: String? = null,
    val coordinates: List<Double> = emptyList(),
)

/**
 * Bilingual scalar wrapper — the dashboard schema serves every value as
 * `{"en": X, "fr": Y}` where X/Y may be a string or a number. Numeric
 * values are language-independent (en preferred, fr fallback); text is
 * resolved by requested language with cross-language fallback.
 */
@Serializable
data class EcccLocalized(
    val en: JsonElement? = null,
    val fr: JsonElement? = null,
) {
    fun text(language: String): String? {
        val preferred = if (language == "fr") fr else en
        val fallback = if (language == "fr") en else fr
        return preferred.primitiveContent() ?: fallback.primitiveContent()
    }

    fun double(): Double? = en.primitiveDouble() ?: fr.primitiveDouble()

    fun int(): Int? = en.primitiveInt() ?: fr.primitiveInt() ?: double()?.toInt()

    private fun JsonElement?.primitiveContent(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun JsonElement?.primitiveDouble(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()
    }

    private fun JsonElement?.primitiveInt(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }
}

/** `{units, unitType, qaValue, value: {en, fr}}` measurement wrapper. */
@Serializable
data class EcccQuantity(
    val value: EcccLocalized? = null,
    val units: EcccLocalized? = null,
)

/** `{format, value, url}` — `value` is the numeric icon code (rarely a string). */
@Serializable
data class EcccIcon(
    val value: JsonElement? = null,
    val format: String? = null,
    val url: String? = null,
)

@Serializable
data class EcccWind(
    val speed: EcccQuantity? = null,
    val gust: EcccQuantity? = null,
    val bearing: EcccQuantity? = null,
    val direction: EcccQuantity? = null,
)

@Serializable
data class EcccCurrentConditions(
    val condition: EcccLocalized? = null,
    @SerialName("iconCode") val iconCode: EcccIcon? = null,
    val timestamp: EcccLocalized? = null,
    val temperature: EcccQuantity? = null,
    val dewpoint: EcccQuantity? = null,
    val pressure: EcccQuantity? = null, // kPa
    val relativeHumidity: EcccQuantity? = null,
    val wind: EcccWind? = null,
    val visibility: EcccQuantity? = null, // km, not always published
    val windChill: EcccQuantity? = null,
    val humidex: EcccQuantity? = null,
)

@Serializable
data class EcccPeriod(
    @SerialName("textForecastName") val textForecastName: EcccLocalized? = null,
    /** English weekday-style period name ("Friday", "Friday night") in `en`. */
    val value: EcccLocalized? = null,
)

@Serializable
data class EcccTemperatureEntry(
    @SerialName("class") val tempClass: EcccLocalized? = null,
    val value: EcccLocalized? = null,
)

@Serializable
data class EcccTemperatures(
    val temperature: List<EcccTemperatureEntry> = emptyList(),
    val textSummary: EcccLocalized? = null,
)

@Serializable
data class EcccAbbreviatedForecast(
    val icon: EcccIcon? = null,
    val textSummary: EcccLocalized? = null,
    /** Probability of precipitation — omitted upstream when nil. */
    val pop: EcccQuantity? = null,
)

/**
 * `uv.index` is `{en, fr}` directly on daily entries but `{value: {en, fr}}`
 * on hourly entries — keep it flexible.
 */
@Serializable
data class EcccUv(
    val index: JsonElement? = null,
) {
    fun indexValue(): Double? = flexibleDouble(index)
}

@Serializable
data class EcccForecastEntry(
    val period: EcccPeriod? = null,
    val textSummary: EcccLocalized? = null,
    val temperatures: EcccTemperatures? = null,
    @SerialName("abbreviatedForecast") val abbreviatedForecast: EcccAbbreviatedForecast? = null,
    val uv: EcccUv? = null,
    val relativeHumidity: EcccQuantity? = null,
)

@Serializable
data class EcccForecastGroup(
    val forecasts: List<EcccForecastEntry> = emptyList(),
)

@Serializable
data class EcccHourlyEntry(
    /** UTC instant, e.g. "2026-07-17T10:00:00Z". */
    val timestamp: String? = null,
    val condition: EcccLocalized? = null,
    @SerialName("iconCode") val iconCode: EcccIcon? = null,
    val temperature: EcccQuantity? = null,
    /** Likelihood of precipitation, percent. */
    val lop: EcccQuantity? = null,
    val wind: EcccWind? = null,
    val uv: EcccUv? = null,
)

@Serializable
data class EcccHourlyForecastGroup(
    @SerialName("hourlyForecasts") val hourlyForecasts: List<EcccHourlyEntry> = emptyList(),
)

@Serializable
data class EcccRiseSet(
    /** UTC instants. */
    val sunrise: EcccLocalized? = null,
    val sunset: EcccLocalized? = null,
)

@Serializable
data class EcccProperties(
    val name: EcccLocalized? = null,
    @SerialName("lastUpdated") val lastUpdated: String? = null,
    val currentConditions: EcccCurrentConditions? = null,
    val forecastGroup: EcccForecastGroup? = null,
    @SerialName("hourlyForecastGroup") val hourlyForecastGroup: EcccHourlyForecastGroup? = null,
    @SerialName("riseSet") val riseSet: EcccRiseSet? = null,
)

/**
 * Resolves a numeric value from the schema's flexible shapes: a raw
 * primitive, an `{en, fr}` localized object, or a `{value: {en, fr}}`
 * quantity object.
 */
internal fun flexibleDouble(element: JsonElement?): Double? {
    when (element) {
        null -> return null
        is JsonPrimitive -> return element.doubleOrNull ?: element.contentOrNull?.trim()?.toDoubleOrNull()
        is JsonObject -> {
            val nested = element["value"] ?: element["en"] ?: element["fr"] ?: return null
            return flexibleDouble(nested)
        }
        else -> return null
    }
}
