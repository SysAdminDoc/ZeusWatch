package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.HkoApi
import com.sysadmindoc.nimbus.data.api.HkoCurrentReportResponse
import com.sysadmindoc.nimbus.data.api.HkoForecastDay
import com.sysadmindoc.nimbus.data.api.HkoForecastResponse
import com.sysadmindoc.nimbus.data.api.HkoLocalForecastResponse
import com.sysadmindoc.nimbus.data.api.HkoObservationBlock
import com.sysadmindoc.nimbus.data.api.HkoObservationEntry
import com.sysadmindoc.nimbus.data.api.HkoRainfallBlock
import com.sysadmindoc.nimbus.data.api.HkoRainfallEntry
import com.sysadmindoc.nimbus.data.api.HkoValueUnit
import com.sysadmindoc.nimbus.data.api.HkoWarningDetail
import com.sysadmindoc.nimbus.data.api.HkoWarningInfoResponse
import com.sysadmindoc.nimbus.data.api.HkoWarningSummary
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HkoApiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun parsesForecastCurrentAndWarningPayloadShapes() {
        val forecast = json.decodeFromString<HkoForecastResponse>(
            """
            {
              "weatherForecast": [{
                "forecastDate": "20260702",
                "forecastMaxtemp": {"value": 33, "unit": "C"},
                "forecastMintemp": {"value": 28, "unit": "C"},
                "ForecastIcon": 62,
                "PSR": "Medium High"
              }],
              "updateTime": "2026-07-02T01:50:00+08:00"
            }
            """.trimIndent(),
        )
        val current = json.decodeFromString<HkoCurrentReportResponse>(
            """
            {
              "uvindex": "",
              "temperature": {
                "data": [{"place": "Hong Kong Observatory", "value": 29, "unit": "C"}],
                "recordTime": "2026-07-02T02:00:00+08:00"
              },
              "humidity": {
                "data": [{"place": "Hong Kong Observatory", "value": 82, "unit": "percent"}]
              }
            }
            """.trimIndent(),
        )
        val warnings = json.decodeFromString<Map<String, HkoWarningSummary>>(
            """
            {
              "WRAIN": {
                "name": "Rainstorm Warning Signal",
                "code": "WRAINR",
                "type": "Red",
                "actionCode": "ISSUE"
              }
            }
            """.trimIndent(),
        )
        val noWarningInfo = json.decodeFromString<HkoWarningInfoResponse>("{}")

        assertEquals(33.0, forecast.weatherForecast.single().forecastMaxtemp?.value)
        assertEquals(29.0, current.temperature?.data?.single()?.value)
        assertEquals("WRAINR", warnings.getValue("WRAIN").code)
        assertTrue(noWarningInfo.details.isEmpty())
    }
}

class HkoForecastAdapterTest {

    @Test
    fun mapsCurrentConditionsAndNineDayForecastInMetricUnits() = runTest {
        val api = mockk<HkoApi>()
        coEvery { api.getForecast9Day(any(), any()) } returns HkoForecastResponse(
            weatherForecast = listOf(
                HkoForecastDay(
                    forecastDate = "20260702",
                    forecastWeather = "Sunny periods and a few showers.",
                    forecastMaxtemp = HkoValueUnit(value = 33.0, unit = "C"),
                    forecastMintemp = HkoValueUnit(value = 28.0, unit = "C"),
                    forecastIcon = 53,
                    probabilityOfSignificantRain = "Medium Low",
                ),
                HkoForecastDay(
                    forecastDate = "20260703",
                    forecastWeather = "Heavy showers and thunderstorms.",
                    forecastMaxtemp = HkoValueUnit(value = 31.0, unit = "C"),
                    forecastMintemp = HkoValueUnit(value = 27.0, unit = "C"),
                    forecastIcon = 65,
                    probabilityOfSignificantRain = "High",
                ),
            ),
            updateTime = "2026-07-02T01:50:00+08:00",
        )
        coEvery { api.getCurrentReport(any(), any()) } returns HkoCurrentReportResponse(
            rainfall = HkoRainfallBlock(
                data = listOf(
                    HkoRainfallEntry(place = "Islands District", max = 2.0, unit = "mm"),
                    HkoRainfallEntry(place = "Central & Western District", max = 0.0, unit = "mm"),
                ),
            ),
            icon = listOf(62),
            uvindex = JsonObject(
                mapOf(
                    "data" to JsonArray(
                        listOf(JsonObject(mapOf("value" to JsonPrimitive(7.0))))
                    )
                )
            ),
            updateTime = "2026-07-02T02:02:00+08:00",
            temperature = HkoObservationBlock(
                recordTime = "2026-07-02T02:00:00+08:00",
                data = listOf(
                    HkoObservationEntry(place = "King's Park", value = 30.0, unit = "C"),
                    HkoObservationEntry(place = "Hong Kong Observatory", value = 29.0, unit = "C"),
                ),
            ),
            humidity = HkoObservationBlock(
                recordTime = "2026-07-02T02:00:00+08:00",
                data = listOf(HkoObservationEntry(place = "Hong Kong Observatory", value = 82.0, unit = "percent")),
            ),
        )
        coEvery { api.getLocalForecast(any(), any()) } returns HkoLocalForecastResponse(
            forecastDesc = "Mainly cloudy with a few showers.",
            updateTime = "2026-07-02T02:45:00+08:00",
        )

        val data = HkoForecastAdapter(api).getWeather(22.3027, 114.1772, "Tsim Sha Tsui").getOrThrow()

        assertEquals("Tsim Sha Tsui", data.location.name)
        assertEquals("HK", data.location.country)
        assertEquals("Asia/Hong_Kong", data.location.timeZone)
        assertEquals(29.0, data.current.temperature, 0.0)
        assertEquals(82, data.current.humidity)
        assertEquals(WeatherCode.RAIN_SLIGHT, data.current.weatherCode)
        assertEquals(2.0, data.current.precipitation, 0.0)
        assertEquals(7.0, data.current.uvIndex, 0.0)
        assertTrue(data.hourly.isEmpty())
        assertEquals(2, data.daily.size)
        assertEquals(LocalDate.of(2026, 7, 2), data.daily.first().date)
        assertEquals(33.0, data.daily.first().temperatureHigh, 0.0)
        assertEquals(28.0, data.daily.first().temperatureLow, 0.0)
        assertEquals(35, data.daily.first().precipitationProbability)
    }

    @Test
    fun rejectsCoordinatesOutsideHongKongSoFallbackCanRun() = runTest {
        val result = HkoForecastAdapter(mockk(relaxed = true)).getWeather(40.7128, -74.0060, "New York")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("limited to Hong Kong"))
    }
}

class HkoAlertAdapterTest {

    @Test
    fun mapsWarningInfoWithSummarySeverityAndTiming() = runTest {
        val api = mockk<HkoApi>()
        coEvery { api.getWarningSummary(any(), any()) } returns mapOf(
            "WRAIN" to HkoWarningSummary(
                name = "Rainstorm Warning Signal",
                code = "WRAINR",
                type = "Red",
                actionCode = "ISSUE",
                issueTime = "2026-07-02T02:00:00+08:00",
                expireTime = "2026-07-02T05:00:00+08:00",
                updateTime = "2026-07-02T02:10:00+08:00",
            )
        )
        coEvery { api.getWarningInfo(any(), any()) } returns HkoWarningInfoResponse(
            details = listOf(
                HkoWarningDetail(
                    warningStatementCode = "WRAIN",
                    subtype = "WRAINR",
                    contents = listOf("Red rainstorm warning is now in force."),
                    updateTime = "2026-07-02T02:10:00+08:00",
                )
            )
        )

        val alert = HkoAlertAdapter(api).getAlerts(22.3027, 114.1772).getOrThrow().single()

        assertEquals("Rainstorm Warning Signal", alert.event)
        assertEquals("Red rainstorm warning is now in force.", alert.description)
        assertEquals(AlertSeverity.SEVERE, alert.severity)
        assertEquals(AlertUrgency.IMMEDIATE, alert.urgency)
        assertEquals("Hong Kong Observatory", alert.senderName)
        assertEquals("2026-07-02T02:00:00+08:00", alert.effective)
        assertEquals("2026-07-02T05:00:00+08:00", alert.expires)
        assertEquals(true, alert.coversRequestedLocation)
    }

    @Test
    fun emptyWarningPayloadIsAValidNoAlertsResponse() = runTest {
        val api = mockk<HkoApi>()
        coEvery { api.getWarningSummary(any(), any()) } returns emptyMap()
        coEvery { api.getWarningInfo(any(), any()) } returns HkoWarningInfoResponse()

        val alerts = HkoAlertAdapter(api).getAlerts(22.3027, 114.1772).getOrThrow()

        assertTrue(alerts.isEmpty())
    }
}
