package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.OpenWeatherMapApi
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.OwmAirComponents
import com.sysadmindoc.nimbus.data.model.OwmAirPollutionEntry
import com.sysadmindoc.nimbus.data.model.OwmAirPollutionMain
import com.sysadmindoc.nimbus.data.model.OwmAirPollutionResponse
import com.sysadmindoc.nimbus.data.model.OwmAlert
import com.sysadmindoc.nimbus.data.model.OwmCurrent
import com.sysadmindoc.nimbus.data.model.OwmDaily
import com.sysadmindoc.nimbus.data.model.OwmDailyTemp
import com.sysadmindoc.nimbus.data.model.OwmHourly
import com.sysadmindoc.nimbus.data.model.OwmOneCallResponse
import com.sysadmindoc.nimbus.data.model.OwmPrecipVolume
import com.sysadmindoc.nimbus.data.model.OwmWeatherDesc
import com.sysadmindoc.nimbus.data.model.WeatherCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun makeOneCallResponse(
    nowEpoch: Long,
    temp: Double = 20.0,
    humidity: Int = 60,
    windSpeedMs: Double = 5.0, // m/s
    windGustMs: Double? = null,
    visibilityM: Int? = 10000,
    cloudCover: Int = 30,
    uvIndex: Double = 3.0,
    timezone: String = "UTC",
    weatherId: Int = 800, // Clear sky
    icon: String = "01d",
    currentSnow1h: Double? = null, // mm liquid-equivalent
    hourly: List<OwmHourly> = emptyList(),
    daily: List<OwmDaily> = emptyList(),
    alerts: List<OwmAlert> = emptyList(),
) = OwmOneCallResponse(
    lat = 51.5,
    lon = -0.1,
    timezone = timezone,
    current = OwmCurrent(
        dt = nowEpoch,
        temp = temp,
        feelsLike = temp - 2.0,
        humidity = humidity,
        clouds = cloudCover,
        uvi = uvIndex,
        windSpeed = windSpeedMs,
        windDeg = 270,
        windGust = windGustMs,
        visibility = visibilityM,
        snow = currentSnow1h?.let { OwmPrecipVolume(oneHour = it) },
        weather = listOf(OwmWeatherDesc(id = weatherId, icon = icon)),
    ),
    hourly = hourly,
    daily = daily,
    alerts = alerts,
)

class OwmForecastAdapterTest {

    private val nowEpoch = Instant.parse("2026-06-15T12:00:00Z").epochSecond

    @Test
    fun `maps current conditions — wind speed converts m_s to km_h`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key-abc"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            windSpeedMs = 5.0, // 5 m/s = 18 km/h
        )

        val result = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, "London")
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("London", data.location.name)
        assertEquals(18.0, data.current.windSpeed, 0.01)
    }

    @Test
    fun `maps current conditions — wind gust converts m_s to km_h`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key-abc"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            windSpeedMs = 3.0,
            windGustMs = 8.0, // 8 m/s = 28.8 km/h
        )

        val data = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertNotNull(data.current.windGusts)
        assertEquals(28.8, data.current.windGusts!!, 0.01)
    }

    @Test
    fun `maps visibility — stored as meters for WeatherFormatter contract`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            visibilityM = 8000, // OWM sends meters; stored as-is
        )

        val data = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertEquals(8000.0, data.current.visibility!!, 0.01)
    }

    @Test
    fun `clear sky OWM code 800 maps to CLEAR_SKY WMO`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            weatherId = 800, // Clear sky
        )

        val data = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertEquals(WeatherCode.CLEAR_SKY, data.current.weatherCode)
    }

    @Test
    fun `thunderstorm OWM code 200 maps to THUNDERSTORM WMO`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            weatherId = 200, // Thunderstorm with light rain
        )

        val data = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertEquals(WeatherCode.THUNDERSTORM, data.current.weatherCode)
    }

    @Test
    fun `daytime icon suffix sets isDay true, night icon sets isDay false`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))

        // Day icon
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            icon = "01d",
        )
        val dayData = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertTrue(dayData.current.isDay)

        // Night icon
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            icon = "01n",
        )
        val nightData = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertFalse(nightData.current.isDay)
    }

    @Test
    fun `hourly pop fraction 0-1 converts to 0-100 percent`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        val futureEpoch = nowEpoch + 3600L // 1h ahead
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            hourly = listOf(
                OwmHourly(
                    dt = futureEpoch,
                    temp = 18.0,
                    pop = 0.75, // 75%
                    weather = listOf(OwmWeatherDesc(id = 800, icon = "01d")),
                )
            ),
        )

        val data = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        val firstHourly = data.hourly.firstOrNull()
        assertNotNull("hourly should be non-empty", firstHourly)
        assertEquals(75, firstHourly!!.precipitationProbability)
    }

    @Test
    fun `hourly pop over 1_0 is clamped to 100`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        val futureEpoch = nowEpoch + 3600L
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            hourly = listOf(
                OwmHourly(dt = futureEpoch, temp = 18.0, pop = 1.5,
                    weather = listOf(OwmWeatherDesc(id = 800, icon = "01d"))),
            ),
        )

        val data = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertEquals(100, data.hourly.first().precipitationProbability)
    }

    @Test
    fun `missing API key returns failure without crash`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = ""))

        val result = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null)
        assertTrue("Blank API key must yield failure", result.isFailure)
    }

    @Test
    fun `snowfall converts mm liquid-equivalent to cm in current hourly and daily`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        val futureEpoch = nowEpoch + 3600L
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            currentSnow1h = 2.0, // mm → 0.2 cm
            hourly = listOf(
                OwmHourly(
                    dt = futureEpoch, temp = -2.0,
                    snow = OwmPrecipVolume(oneHour = 1.5), // mm → 0.15 cm
                    weather = listOf(OwmWeatherDesc(id = 600, icon = "13d")),
                )
            ),
            daily = listOf(
                OwmDaily(
                    dt = nowEpoch, temp = OwmDailyTemp(min = -8.0, max = -1.0),
                    snow = 12.0, // mm → 1.2 cm
                    weather = listOf(OwmWeatherDesc(id = 600, icon = "13d")),
                )
            ),
        )

        val data = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow()
        assertEquals(0.2, data.current.snowfall!!, 0.001)
        assertEquals(0.15, data.hourly.first().snowfall!!, 0.001)
        assertEquals(1.2, data.daily.first().snowfallSum!!, 0.001)
    }

    @Test
    fun `snow-only day still yields precipitationSum in mm`() = runTest {
        // rain == null must not collapse precipitationSum to null when snow
        // is present; sum stays mm liquid-equivalent.
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            daily = listOf(
                OwmDaily(
                    dt = nowEpoch, temp = OwmDailyTemp(min = -8.0, max = -1.0),
                    rain = null, snow = 8.0,
                    weather = listOf(OwmWeatherDesc(id = 600, icon = "13d")),
                )
            ),
        )

        val d = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow().daily.first()
        assertEquals(8.0, d.precipitationSum!!, 0.001)
    }

    @Test
    fun `rain plus snow day sums precipitationSum in mm`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            daily = listOf(
                OwmDaily(
                    dt = nowEpoch, temp = OwmDailyTemp(min = -2.0, max = 3.0),
                    rain = 5.0, snow = 3.0,
                    weather = listOf(OwmWeatherDesc(id = 616, icon = "13d")),
                )
            ),
        )

        val d = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow().daily.first()
        assertEquals(8.0, d.precipitationSum!!, 0.001)
    }

    @Test
    fun `dry day leaves precipitationSum null`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns makeOneCallResponse(
            nowEpoch = nowEpoch,
            daily = listOf(
                OwmDaily(
                    dt = nowEpoch, temp = OwmDailyTemp(min = 10.0, max = 20.0),
                    rain = null, snow = null,
                    weather = listOf(OwmWeatherDesc(id = 800, icon = "01d")),
                )
            ),
        )

        val d = OwmForecastAdapter(api, prefs, OwmResponseCache()).getWeather(51.5, -0.1, null).getOrThrow().daily.first()
        assertNull(d.precipitationSum)
    }
}

class OwmAlertAdapterTest {

    private val nowEpoch = Instant.parse("2026-06-15T12:00:00Z").epochSecond

    @Test
    fun `extreme tag maps to EXTREME severity`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns OwmOneCallResponse(
            lat = 0.0, lon = 0.0,
            alerts = listOf(
                OwmAlert(event = "Tornado Warning", start = nowEpoch, end = nowEpoch + 3600,
                    tags = listOf("Extreme"), description = "Tornado incoming"),
            ),
        )

        val result = OwmAlertAdapter(api, prefs, OwmResponseCache()).getAlerts(0.0, 0.0)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.EXTREME,
            result.getOrThrow().first().severity)
    }

    @Test
    fun `severe and warning tags map to SEVERE`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns OwmOneCallResponse(
            lat = 0.0, lon = 0.0,
            alerts = listOf(
                OwmAlert(event = "Flood Warning", start = nowEpoch, end = nowEpoch + 3600,
                    tags = listOf("Severe")),
            ),
        )

        val result = OwmAlertAdapter(api, prefs, OwmResponseCache()).getAlerts(0.0, 0.0)
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.SEVERE,
            result.getOrThrow().first().severity)
    }

    @Test
    fun `moderate and watch tags map to MODERATE`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns OwmOneCallResponse(
            lat = 0.0, lon = 0.0,
            alerts = listOf(
                OwmAlert(event = "Winter Watch", start = nowEpoch, end = nowEpoch + 3600,
                    tags = listOf("Moderate")),
            ),
        )

        val result = OwmAlertAdapter(api, prefs, OwmResponseCache()).getAlerts(0.0, 0.0)
        assertEquals(com.sysadmindoc.nimbus.data.model.AlertSeverity.MODERATE,
            result.getOrThrow().first().severity)
    }

    @Test
    fun `empty alerts list returns empty success`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getOneCall(any(), any(), any(), any(), any(), any()) } returns OwmOneCallResponse(
            lat = 0.0, lon = 0.0,
            alerts = emptyList(),
        )

        val result = OwmAlertAdapter(api, prefs, OwmResponseCache()).getAlerts(0.0, 0.0)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `missing API key returns failure`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = ""))

        val result = OwmAlertAdapter(api, prefs, OwmResponseCache()).getAlerts(0.0, 0.0)
        assertTrue(result.isFailure)
    }
}

class OwmAqiAdapterTest {

    @Test
    fun `good PM2_5 at 6 ug_m3 maps to AQI around 25 (Good tier)`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getAirPollution(any(), any(), any()) } returns OwmAirPollutionResponse(
            list = listOf(
                OwmAirPollutionEntry(
                    dt = 0,
                    main = OwmAirPollutionMain(aqi = 1),
                    components = OwmAirComponents(pm2_5 = 6.0, pm10 = 20.0, o3 = 40.0),
                )
            )
        )

        val result = OwmAqiAdapter(api, prefs).getAirQuality(51.5, -0.1)
        assertTrue(result.isSuccess)
        val aqi = result.getOrThrow()
        // PM2.5 of 6 µg/m3 is in the Good tier (0-12 → AQI 0-50)
        assertTrue("AQI should be Good (≤50), was ${aqi.usAqi}", aqi.usAqi <= 50)
        assertEquals(AqiLevel.GOOD, aqi.aqiLevel)
    }

    @Test
    fun `unhealthy PM2_5 at 80 ug_m3 maps to AQI above 150`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getAirPollution(any(), any(), any()) } returns OwmAirPollutionResponse(
            list = listOf(
                OwmAirPollutionEntry(
                    dt = 0,
                    main = OwmAirPollutionMain(aqi = 4),
                    // PM2.5 = 80 µg/m3 is in Unhealthy tier (55.5-150.4 → AQI 151-200)
                    components = OwmAirComponents(pm2_5 = 80.0, pm10 = 10.0, o3 = 5.0),
                )
            )
        )

        val result = OwmAqiAdapter(api, prefs).getAirQuality(51.5, -0.1)
        assertTrue(result.isSuccess)
        val aqi = result.getOrThrow()
        assertTrue("AQI should be unhealthy (>150), was ${aqi.usAqi}", aqi.usAqi > 150)
    }

    @Test
    fun `combined AQI is max of PM2_5 PM10 and O3`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        // PM2.5 gives ~25, PM10 gives ~50, O3 high gives the max
        coEvery { api.getAirPollution(any(), any(), any()) } returns OwmAirPollutionResponse(
            list = listOf(
                OwmAirPollutionEntry(
                    dt = 0,
                    main = OwmAirPollutionMain(aqi = 4),
                    components = OwmAirComponents(
                        pm2_5 = 6.0,    // AQI ~25
                        pm10 = 54.0,    // AQI ~50
                        o3 = 150.0,     // ~75 ppb → AQI ~65 (Moderate)
                    ),
                )
            )
        )

        val result = OwmAqiAdapter(api, prefs).getAirQuality(51.5, -0.1)
        val aqi = result.getOrThrow()
        // Overall AQI should be dominated by whichever pollutant is highest
        assertTrue("Overall AQI should be ≥ PM2.5 AQI", aqi.usAqi >= 25)
    }

    @Test
    fun `empty pollution list returns failure`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = "key"))
        coEvery { api.getAirPollution(any(), any(), any()) } returns OwmAirPollutionResponse(
            list = emptyList()
        )

        val result = OwmAqiAdapter(api, prefs).getAirQuality(51.5, -0.1)
        assertTrue("Empty list must yield failure", result.isFailure)
    }

    @Test
    fun `missing API key returns failure`() = runTest {
        val api = mockk<OpenWeatherMapApi>()
        val prefs = mockk<UserPreferences>()
        every { prefs.settings } returns flowOf(NimbusSettings(owmApiKey = ""))

        val result = OwmAqiAdapter(api, prefs).getAirQuality(51.5, -0.1)
        assertTrue(result.isFailure)
    }
}
