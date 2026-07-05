package com.sysadmindoc.nimbus.data.repository

import com.openmeteo.sdk.Aggregation
import com.openmeteo.sdk.Variable
import com.openmeteo.sdk.VariableWithValues
import com.openmeteo.sdk.VariablesWithTime
import com.openmeteo.sdk.WeatherApiResponse
import com.sysadmindoc.nimbus.data.model.CurrentWeather
import com.sysadmindoc.nimbus.data.model.DailyWeather
import com.sysadmindoc.nimbus.data.model.HourlyWeather
import com.sysadmindoc.nimbus.data.model.OpenMeteoResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Decodes Open-Meteo's size-prefixed FlatBuffers weather payload into the
 * existing JSON response model so the production mapper/cache path stays shared.
 */
@Singleton
class OpenMeteoFlatBufferAdapter @Inject constructor() {

    fun decodeForecast(bytes: ByteArray): OpenMeteoResponse {
        require(bytes.size > SIZE_PREFIX_BYTES) { "Open-Meteo FlatBuffer payload is empty" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(SIZE_PREFIX_BYTES)
        val response = WeatherApiResponse.getRootAsWeatherApiResponse(buffer)
        val offsetSeconds = response.utcOffsetSeconds()
        return OpenMeteoResponse(
            latitude = response.latitude().toDouble(),
            longitude = response.longitude().toDouble(),
            elevation = response.elevation().toDouble(),
            generationTimeMs = response.generationTimeMilliseconds().toDouble(),
            utcOffsetSeconds = offsetSeconds,
            timezone = response.timezone(),
            timezoneAbbreviation = response.timezoneAbbreviation(),
            current = response.current()?.toCurrentWeather(offsetSeconds),
            hourly = response.hourly()?.toHourlyWeather(offsetSeconds),
            daily = response.daily()?.toDailyWeather(offsetSeconds),
            minutely15 = null,
        )
    }

    private fun VariablesWithTime.toCurrentWeather(offsetSeconds: Int): CurrentWeather {
        return CurrentWeather(
            time = dateTimeLabel(time(), offsetSeconds),
            interval = interval().takeIf { it > 0 },
            temperature = firstValue(Variable.temperature, altitude = 2),
            humidity = firstIntValue(Variable.relative_humidity, altitude = 2),
            apparentTemperature = firstValue(Variable.apparent_temperature, altitude = 2),
            isDay = firstIntValue(Variable.is_day),
            precipitation = firstValue(Variable.precipitation),
            weatherCode = firstIntValue(Variable.weather_code),
            cloudCover = firstIntValue(Variable.cloud_cover),
            pressureMsl = firstValue(Variable.pressure_msl),
            surfacePressure = firstValue(Variable.surface_pressure),
            windSpeed = firstValue(Variable.wind_speed, altitude = 10),
            windDirection = firstIntValue(Variable.wind_direction, altitude = 10),
            windGusts = firstValue(Variable.wind_gusts, altitude = 10),
            uvIndex = firstValue(Variable.uv_index),
            visibility = firstValue(Variable.visibility),
            dewPoint = firstValue(Variable.dew_point, altitude = 2),
            snowfall = firstValue(Variable.snowfall),
            snowDepth = firstValue(Variable.snow_depth),
            cape = firstValue(Variable.cape),
        )
    }

    private fun VariablesWithTime.toHourlyWeather(offsetSeconds: Int): HourlyWeather {
        val count = maxValueCount()
        return HourlyWeather(
            time = labels(count, offsetSeconds, DATE_TIME_FORMATTER),
            temperature = values(Variable.temperature, altitude = 2),
            humidity = intValues(Variable.relative_humidity, altitude = 2),
            apparentTemperature = values(Variable.apparent_temperature, altitude = 2),
            precipitationProbability = intValues(Variable.precipitation_probability),
            precipitation = values(Variable.precipitation),
            weatherCode = intValues(Variable.weather_code),
            cloudCover = intValues(Variable.cloud_cover),
            visibility = values(Variable.visibility),
            windSpeed = values(Variable.wind_speed, altitude = 10),
            windDirection = intValues(Variable.wind_direction, altitude = 10),
            uvIndex = values(Variable.uv_index),
            isDay = intValues(Variable.is_day),
            snowfall = values(Variable.snowfall),
            snowDepth = values(Variable.snow_depth),
            windGusts = values(Variable.wind_gusts, altitude = 10),
            sunshineDuration = values(Variable.sunshine_duration),
            cape = values(Variable.cape),
            surfacePressure = values(Variable.surface_pressure),
            shortwaveRadiation = values(Variable.shortwave_radiation),
            directNormalIrradiance = values(Variable.direct_normal_irradiance),
        )
    }

    private fun VariablesWithTime.toDailyWeather(offsetSeconds: Int): DailyWeather {
        val count = maxValueCount()
        return DailyWeather(
            time = labels(count, offsetSeconds, DATE_FORMATTER),
            weatherCode = intValues(Variable.weather_code),
            temperatureMax = values(Variable.temperature, altitude = 2, aggregation = Aggregation.maximum),
            temperatureMin = values(Variable.temperature, altitude = 2, aggregation = Aggregation.minimum),
            apparentTemperatureMax = values(
                Variable.apparent_temperature,
                altitude = 2,
                aggregation = Aggregation.maximum,
            ),
            apparentTemperatureMin = values(
                Variable.apparent_temperature,
                altitude = 2,
                aggregation = Aggregation.minimum,
            ),
            sunrise = timeValues(Variable.sunrise, count, offsetSeconds),
            sunset = timeValues(Variable.sunset, count, offsetSeconds),
            uvIndexMax = values(Variable.uv_index, aggregation = Aggregation.maximum),
            precipitationSum = values(Variable.precipitation, aggregation = Aggregation.sum),
            precipitationProbabilityMax = intValues(
                Variable.precipitation_probability,
                aggregation = Aggregation.maximum,
            ),
            windSpeedMax = values(Variable.wind_speed, altitude = 10, aggregation = Aggregation.maximum),
            windDirectionDominant = intValues(
                Variable.wind_direction,
                altitude = 10,
                aggregation = Aggregation.dominant,
            ),
            precipitationHours = values(Variable.precipitation_hours, aggregation = Aggregation.sum),
            snowfallSum = values(Variable.snowfall, aggregation = Aggregation.sum),
            sunshineDuration = values(Variable.sunshine_duration, aggregation = Aggregation.sum),
            windGustsMax = values(Variable.wind_gusts, altitude = 10, aggregation = Aggregation.maximum),
        )
    }

    private fun VariablesWithTime.firstValue(
        variable: Int,
        altitude: Int? = null,
        aggregation: Int? = null,
    ): Double? = variable(variable, altitude, aggregation)?.value()?.toNullableDouble()

    private fun VariablesWithTime.firstIntValue(
        variable: Int,
        altitude: Int? = null,
        aggregation: Int? = null,
    ): Int? = firstValue(variable, altitude, aggregation)?.roundToInt()

    private fun VariablesWithTime.values(
        variable: Int,
        altitude: Int? = null,
        aggregation: Int? = null,
    ): List<Double?>? = variable(variable, altitude, aggregation)?.values()

    private fun VariablesWithTime.intValues(
        variable: Int,
        altitude: Int? = null,
        aggregation: Int? = null,
    ): List<Int?>? = values(variable, altitude, aggregation)?.map { it?.roundToInt() }

    private fun VariablesWithTime.timeValues(
        variable: Int,
        count: Int,
        offsetSeconds: Int,
    ): List<String?>? {
        val values = variable(variable) ?: return null
        val length = values.valuesInt64Length()
        if (length <= 0) return null
        return (0 until minOf(count, length)).map { index ->
            dateTimeLabel(values.valuesInt64(index), offsetSeconds)
        }
    }

    private fun VariablesWithTime.variable(
        variable: Int,
        altitude: Int? = null,
        aggregation: Int? = null,
    ): VariableWithValues? {
        var fallback: VariableWithValues? = null
        for (index in 0 until variablesLength()) {
            val candidate = variables(index) ?: continue
            if (candidate.variable() != variable) continue
            if (aggregation != null && candidate.aggregation() != aggregation) continue
            if (altitude != null && candidate.altitude().toInt() != altitude) {
                fallback = fallback ?: candidate
                continue
            }
            return candidate
        }
        return fallback.takeIf { altitude != null }
    }

    private fun VariableWithValues.values(): List<Double?> =
        (0 until valuesLength()).map { index -> values(index).toNullableDouble() }

    private fun VariablesWithTime.maxValueCount(): Int {
        var max = 0
        for (index in 0 until variablesLength()) {
            val variable = variables(index) ?: continue
            max = maxOf(max, variable.valuesLength(), variable.valuesInt64Length())
        }
        if (max > 0) return max
        val interval = interval()
        return if (interval > 0 && timeEnd() > time()) {
            ((timeEnd() - time()) / interval).toInt().coerceAtLeast(0)
        } else {
            0
        }
    }

    private fun VariablesWithTime.labels(
        count: Int,
        offsetSeconds: Int,
        formatter: DateTimeFormatter,
    ): List<String> {
        val intervalSeconds = interval().takeIf { it > 0 } ?: return emptyList()
        return (0 until count).map { index ->
            temporalLabel(time() + intervalSeconds.toLong() * index, offsetSeconds, formatter)
        }
    }

    private fun dateTimeLabel(epochSeconds: Long, offsetSeconds: Int): String =
        temporalLabel(epochSeconds, offsetSeconds, DATE_TIME_FORMATTER)

    private fun temporalLabel(
        epochSeconds: Long,
        offsetSeconds: Int,
        formatter: DateTimeFormatter,
    ): String = Instant.ofEpochSecond(epochSeconds + offsetSeconds)
        .atOffset(ZoneOffset.UTC)
        .toLocalDateTime()
        .format(formatter)

    private fun Float.toNullableDouble(): Double? =
        takeIf { it.isFinite() }?.toDouble()

    companion object {
        private const val SIZE_PREFIX_BYTES = 4
        private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
