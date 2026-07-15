package com.sysadmindoc.nimbus.data.repository

import com.google.flatbuffers.FlatBufferBuilder
import com.openmeteo.sdk.Aggregation
import com.openmeteo.sdk.Unit as OpenMeteoUnit
import com.openmeteo.sdk.Variable
import com.openmeteo.sdk.VariableWithValues
import com.openmeteo.sdk.VariablesWithTime
import com.openmeteo.sdk.WeatherApiResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OpenMeteoFlatBufferAdapterTest {

    private val adapter = OpenMeteoFlatBufferAdapter()

    @Test
    fun decodeForecastMapsCurrentHourlyAndDailyBlocks() {
        val payload = forecastPayload()

        val response = adapter.decodeForecast(payload)
        val current = requireNotNull(response.current)
        val hourly = requireNotNull(response.hourly)
        val daily = requireNotNull(response.daily)

        assertEquals(39.75, response.latitude, 0.01)
        assertEquals(-104.98, response.longitude, 0.01)
        assertEquals("Etc/UTC", response.timezone)
        assertEquals("2026-01-02T12:00", current.time)
        assertEquals(21.5, current.temperature!!, 0.01)
        assertEquals(45, current.humidity)
        assertEquals(2, current.weatherCode)
        assertEquals(18.0, hourly.temperature!![1]!!, 0.01)
        assertEquals(61, hourly.weatherCode!![1])
        assertEquals("2026-01-02", daily.time.first())
        assertEquals(24.0, daily.temperatureMax!!.first()!!, 0.01)
        assertEquals(5.0, daily.temperatureMin!!.first()!!, 0.01)
        assertEquals("2026-01-02T07:30", daily.sunrise!!.first())
    }

    @Test
    fun decodeForecastResolvesLabelsWithNamedZoneAcrossDstTransition() {
        val response = adapter.decodeForecast(
            zonedPayload(timezone = "America/Denver", utcOffsetSeconds = -25200),
        )
        val hourly = requireNotNull(response.hourly)
        val daily = requireNotNull(response.daily)

        // 08:00Z is 01:00 MST; DST starts an hour later, so 09:00Z is 03:00 MDT.
        // A single fixed offset cannot label both hours correctly.
        assertEquals("2026-03-08T01:00", hourly.time[0])
        assertEquals("2026-03-08T03:00", hourly.time[1])
        // Summer midnight (06:00Z) must not be shifted to the previous calendar
        // day by the stale winter offset carried in utc_offset_seconds.
        assertEquals("2026-07-01", daily.time.first())
    }

    @Test
    fun decodeForecastFallsBackToFixedOffsetWhenTimezoneUnusable() {
        val response = adapter.decodeForecast(
            zonedPayload(timezone = "", utcOffsetSeconds = -25200),
        )
        val hourly = requireNotNull(response.hourly)

        // Blank zone: labels use the fixed -07:00 offset for the whole range.
        assertEquals("2026-03-08T01:00", hourly.time[0])
        assertEquals("2026-03-08T02:00", hourly.time[1])
    }

    private fun zonedPayload(timezone: String, utcOffsetSeconds: Int): ByteArray {
        val builder = FlatBufferBuilder(1024)
        val timezoneOffset = builder.createString(timezone)
        val timezoneAbbreviationOffset = builder.createString("")
        val hourly = block(
            builder = builder,
            // 2026-03-08T01:00 in Denver, one hour before the spring-forward jump.
            startEpoch = epoch("2026-03-08T08:00:00Z"),
            intervalSeconds = 3600,
            variables = listOf(
                series(builder, Variable.temperature, OpenMeteoUnit.celsius, 1f, 2f, altitude = 2),
            ),
        )
        val daily = block(
            builder = builder,
            // Midnight MDT on 2026-07-01 while the reported offset is still MST.
            startEpoch = epoch("2026-07-01T06:00:00Z"),
            intervalSeconds = 86400,
            variables = listOf(
                series(builder, Variable.weather_code, OpenMeteoUnit.wmo_code, 3f),
            ),
        )
        val root = WeatherApiResponse.createWeatherApiResponse(
            builder,
            39.75f,
            -104.98f,
            1600f,
            1.25f,
            0L,
            0,
            utcOffsetSeconds,
            timezoneOffset,
            timezoneAbbreviationOffset,
            0,
            daily,
            hourly,
            0,
            0,
        )
        WeatherApiResponse.finishSizePrefixedWeatherApiResponseBuffer(builder, root)
        return builder.sizedByteArray()
    }

    private fun forecastPayload(): ByteArray {
        val builder = FlatBufferBuilder(1024)
        val timezoneOffset = builder.createString("Etc/UTC")
        val timezoneAbbreviationOffset = builder.createString("UTC")
        val current = block(
            builder = builder,
            startEpoch = epoch("2026-01-02T12:00:00Z"),
            intervalSeconds = 900,
            variables = listOf(
                value(builder, Variable.temperature, OpenMeteoUnit.celsius, 21.5f, altitude = 2),
                value(builder, Variable.relative_humidity, OpenMeteoUnit.percentage, 45f, altitude = 2),
                value(builder, Variable.apparent_temperature, OpenMeteoUnit.celsius, 20.0f, altitude = 2),
                value(builder, Variable.is_day, OpenMeteoUnit.dimensionless_integer, 1f),
                value(builder, Variable.precipitation, OpenMeteoUnit.millimetre, 0.0f),
                value(builder, Variable.weather_code, OpenMeteoUnit.wmo_code, 2f),
                value(builder, Variable.cloud_cover, OpenMeteoUnit.percentage, 30f),
                value(builder, Variable.pressure_msl, OpenMeteoUnit.hectopascal, 1012.5f),
                value(builder, Variable.wind_speed, OpenMeteoUnit.kilometres_per_hour, 12.0f, altitude = 10),
                value(builder, Variable.wind_direction, OpenMeteoUnit.degree_direction, 220f, altitude = 10),
                value(builder, Variable.uv_index, OpenMeteoUnit.dimensionless, 4.0f),
                value(builder, Variable.visibility, OpenMeteoUnit.metre, 14000f),
            ),
        )
        val hourly = block(
            builder = builder,
            startEpoch = epoch("2026-01-02T12:00:00Z"),
            intervalSeconds = 3600,
            variables = listOf(
                series(builder, Variable.temperature, OpenMeteoUnit.celsius, 17f, 18f, altitude = 2),
                series(builder, Variable.weather_code, OpenMeteoUnit.wmo_code, 3f, 61f),
                series(builder, Variable.wind_speed, OpenMeteoUnit.kilometres_per_hour, 20f, 25f, altitude = 10),
                series(builder, Variable.relative_humidity, OpenMeteoUnit.percentage, 50f, 52f, altitude = 2),
                series(builder, Variable.is_day, OpenMeteoUnit.dimensionless_integer, 1f, 1f),
            ),
        )
        val daily = block(
            builder = builder,
            startEpoch = epoch("2026-01-02T00:00:00Z"),
            intervalSeconds = 86400,
            variables = listOf(
                series(builder, Variable.weather_code, OpenMeteoUnit.wmo_code, 3f),
                series(
                    builder,
                    Variable.temperature,
                    OpenMeteoUnit.celsius,
                    24f,
                    altitude = 2,
                    aggregation = Aggregation.maximum,
                ),
                series(
                    builder,
                    Variable.temperature,
                    OpenMeteoUnit.celsius,
                    5f,
                    altitude = 2,
                    aggregation = Aggregation.minimum,
                ),
                int64Series(
                    builder,
                    Variable.sunrise,
                    OpenMeteoUnit.unix_time,
                    epoch("2026-01-02T07:30:00Z"),
                ),
            ),
        )
        val root = WeatherApiResponse.createWeatherApiResponse(
            builder,
            39.75f,
            -104.98f,
            1600f,
            1.25f,
            0L,
            0,
            0,
            timezoneOffset,
            timezoneAbbreviationOffset,
            current,
            daily,
            hourly,
            0,
            0,
        )
        WeatherApiResponse.finishSizePrefixedWeatherApiResponseBuffer(builder, root)
        return builder.sizedByteArray()
    }

    private fun block(
        builder: FlatBufferBuilder,
        startEpoch: Long,
        intervalSeconds: Int,
        variables: List<Int>,
    ): Int {
        val variablesOffset = VariablesWithTime.createVariablesVector(builder, variables.toIntArray())
        return VariablesWithTime.createVariablesWithTime(
            builder,
            startEpoch,
            startEpoch + intervalSeconds.toLong() * variables.size,
            intervalSeconds,
            variablesOffset,
        )
    }

    private fun value(
        builder: FlatBufferBuilder,
        variable: Int,
        unit: Int,
        value: Float,
        altitude: Int = 0,
        aggregation: Int = 0,
    ): Int = VariableWithValues.createVariableWithValues(
        builder,
        variable,
        unit,
        value,
        0,
        0,
        altitude.toShort(),
        aggregation,
        0,
        0,
        0,
        0,
        0,
    )

    private fun series(
        builder: FlatBufferBuilder,
        variable: Int,
        unit: Int,
        vararg values: Float,
        altitude: Int = 0,
        aggregation: Int = 0,
    ): Int {
        val valuesOffset = VariableWithValues.createValuesVector(builder, values)
        return VariableWithValues.createVariableWithValues(
            builder,
            variable,
            unit,
            0f,
            valuesOffset,
            0,
            altitude.toShort(),
            aggregation,
            0,
            0,
            0,
            0,
            0,
        )
    }

    private fun int64Series(
        builder: FlatBufferBuilder,
        variable: Int,
        unit: Int,
        vararg values: Long,
    ): Int {
        val valuesOffset = VariableWithValues.createValuesInt64Vector(builder, values)
        return VariableWithValues.createVariableWithValues(
            builder,
            variable,
            unit,
            0f,
            0,
            valuesOffset,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
        )
    }

    private fun epoch(value: String): Long = Instant.parse(value).epochSecond
}
