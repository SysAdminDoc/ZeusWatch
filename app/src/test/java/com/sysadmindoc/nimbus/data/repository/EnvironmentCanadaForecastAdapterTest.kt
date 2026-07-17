package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.api.EcccAbbreviatedForecast
import com.sysadmindoc.nimbus.data.api.EcccCurrentConditions
import com.sysadmindoc.nimbus.data.api.EcccFeature
import com.sysadmindoc.nimbus.data.api.EcccFeatureCollection
import com.sysadmindoc.nimbus.data.api.EcccForecastEntry
import com.sysadmindoc.nimbus.data.api.EcccForecastGroup
import com.sysadmindoc.nimbus.data.api.EcccGeometry
import com.sysadmindoc.nimbus.data.api.EcccHourlyEntry
import com.sysadmindoc.nimbus.data.api.EcccHourlyForecastGroup
import com.sysadmindoc.nimbus.data.api.EcccIcon
import com.sysadmindoc.nimbus.data.api.EcccLocalized
import com.sysadmindoc.nimbus.data.api.EcccPeriod
import com.sysadmindoc.nimbus.data.api.EcccProperties
import com.sysadmindoc.nimbus.data.api.EcccQuantity
import com.sysadmindoc.nimbus.data.api.EcccRiseSet
import com.sysadmindoc.nimbus.data.api.EcccTemperatureEntry
import com.sysadmindoc.nimbus.data.api.EcccTemperatures
import com.sysadmindoc.nimbus.data.api.EcccUv
import com.sysadmindoc.nimbus.data.api.EcccWind
import com.sysadmindoc.nimbus.data.api.EnvironmentCanadaForecastApi
import com.sysadmindoc.nimbus.data.model.WeatherCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EnvironmentCanadaForecastAdapterTest {

    private fun localized(en: String, fr: String? = null): EcccLocalized =
        EcccLocalized(en = JsonPrimitive(en), fr = fr?.let { JsonPrimitive(it) })

    private fun quantity(value: Double): EcccQuantity =
        EcccQuantity(value = EcccLocalized(en = JsonPrimitive(value), fr = JsonPrimitive(value)))

    private fun icon(code: Int): EcccIcon = EcccIcon(value = JsonPrimitive(code))

    private fun period(en: String, valueEn: String, valueFr: String? = null): EcccPeriod =
        EcccPeriod(
            textForecastName = localized(en, valueFr?.let { "fr:$en" }),
            value = localized(valueEn, valueFr),
        )

    private fun temperatures(tempClass: String, value: Double): EcccTemperatures =
        EcccTemperatures(
            temperature = listOf(
                EcccTemperatureEntry(
                    tempClass = localized(tempClass),
                    value = EcccLocalized(en = JsonPrimitive(value)),
                ),
            ),
        )

    private fun feature(
        lat: Double,
        lon: Double,
        cityEn: String = "Toronto",
        iconCode: Int? = 3,
        temperature: Double = 12.5,
        humidity: Double = 68.0,
        pressureKpa: Double = 101.3,
        windKmh: Double = 15.0,
        forecasts: List<EcccForecastEntry> = defaultForecast(),
        hourly: List<EcccHourlyEntry> = emptyList(),
        timestampUtc: String = "2026-04-24T12:00:00Z",
        riseSet: EcccRiseSet? = null,
        geometry: EcccGeometry? = EcccGeometry(type = "Point", coordinates = listOf(lon, lat)),
    ): EcccFeature = EcccFeature(
        id = "ecccTest",
        type = "Feature",
        geometry = geometry,
        properties = EcccProperties(
            name = localized(cityEn),
            lastUpdated = timestampUtc,
            currentConditions = EcccCurrentConditions(
                condition = localized("Partly cloudy", "Partiellement nuageux"),
                iconCode = iconCode?.let { icon(it) },
                timestamp = localized(timestampUtc),
                temperature = quantity(temperature),
                dewpoint = quantity(7.5),
                pressure = quantity(pressureKpa),
                relativeHumidity = quantity(humidity),
                wind = EcccWind(
                    speed = quantity(windKmh),
                    gust = quantity(25.0),
                    bearing = quantity(230.0),
                    direction = EcccQuantity(value = localized("SW", "SO")),
                ),
                visibility = quantity(24.0),
            ),
            forecastGroup = EcccForecastGroup(forecasts = forecasts),
            hourlyForecastGroup = EcccHourlyForecastGroup(hourlyForecasts = hourly),
            riseSet = riseSet,
        ),
    )

    // Observation date 2026-04-24 is a Friday, so "Friday" pairs with the
    // current day and "Saturday" is tomorrow — matching the live schema
    // where period.value is always an English weekday-style name.
    private fun defaultForecast(): List<EcccForecastEntry> = listOf(
        EcccForecastEntry(
            period = period("Today", "Friday", "vendredi"),
            textSummary = localized("Partly cloudy. High 15.", "Partiellement nuageux. Maximum 15."),
            temperatures = temperatures("high", 15.0),
            abbreviatedForecast = EcccAbbreviatedForecast(
                icon = icon(3),
                textSummary = localized("Partly cloudy", "Partiellement nuageux"),
                pop = quantity(20.0),
            ),
        ),
        EcccForecastEntry(
            period = period("Tonight", "Friday night", "vendredi soir et nuit"),
            textSummary = localized("Cloudy periods. Low 5."),
            temperatures = temperatures("low", 5.0),
            abbreviatedForecast = EcccAbbreviatedForecast(
                icon = icon(4),
                textSummary = localized("Cloudy periods"),
                pop = quantity(10.0),
            ),
        ),
        EcccForecastEntry(
            period = period("Saturday", "Saturday", "samedi"),
            textSummary = localized("Rain. High 9."),
            temperatures = temperatures("high", 9.0),
            abbreviatedForecast = EcccAbbreviatedForecast(
                icon = icon(12),
                textSummary = localized("Rain", "Pluie"),
                pop = quantity(80.0),
            ),
        ),
        EcccForecastEntry(
            period = period("Saturday night", "Saturday night", "samedi soir et nuit"),
            textSummary = localized("Clearing. Low 2."),
            temperatures = temperatures("low", 2.0),
            abbreviatedForecast = EcccAbbreviatedForecast(
                icon = icon(1),
                textSummary = localized("Clear"),
                pop = quantity(0.0),
            ),
        ),
    )

    @Test
    fun `happy path maps nearest feature and converts units`() = runTest {
        val api = mockk<EnvironmentCanadaForecastApi>()
        // Two cities; Toronto is much closer to the query point.
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            type = "FeatureCollection",
            features = listOf(
                feature(lat = 43.65, lon = -79.38, cityEn = "Toronto"),
                feature(lat = 45.42, lon = -75.69, cityEn = "Ottawa"),
            ),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val data = adapter.getWeather(43.70, -79.40, null, ZoneId.of("America/Toronto")).getOrThrow()

        assertEquals("Toronto", data.location.name)
        assertEquals("CA", data.location.country)
        assertEquals(12.5, data.current.temperature, 0.0001)
        assertEquals("Partly cloudy", data.current.sourceConditionText)
        assertEquals(68, data.current.humidity)
        // kPa → hPa conversion
        assertEquals(1013.0, data.current.pressure, 0.01)
        // km → m conversion on visibility
        assertEquals(24_000.0, data.current.visibility!!, 0.01)
        // Daily was paired into Friday (high 15 / low 5) + Saturday (high 9 / low 2)
        assertEquals(2, data.daily.size)
        assertEquals(15.0, data.daily[0].temperatureHigh, 0.0001)
        assertEquals("Partly cloudy", data.daily[0].sourceConditionText)
        assertEquals(5.0, data.daily[0].temperatureLow, 0.0001)
        assertEquals(80, data.daily[1].precipitationProbability)
    }

    @Test
    fun `hourly forecasts project UTC instants into the location zone`() = runTest {
        val api = mockk<EnvironmentCanadaForecastApi>()
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            features = listOf(
                feature(
                    lat = 45.42,
                    lon = -75.69,
                    cityEn = "Ottawa",
                    hourly = listOf(
                        EcccHourlyEntry(
                            timestamp = "2026-04-24T14:00:00Z",
                            condition = localized("Sunny", "Ensoleillé"),
                            iconCode = icon(0),
                            temperature = quantity(14.0),
                            lop = quantity(30.0),
                            wind = EcccWind(
                                speed = quantity(15.0),
                                direction = EcccQuantity(value = localized("NW", "NO")),
                            ),
                            // Hourly uv.index arrives as {value: {en, fr}} unlike daily.
                            uv = EcccUv(
                                index = buildJsonObject {
                                    put("value", buildJsonObject { put("en", 5) })
                                },
                            ),
                        ),
                    ),
                ),
            ),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val data = adapter.getWeather(45.42, -75.69, null, ZoneId.of("America/Toronto")).getOrThrow()

        assertEquals(1, data.hourly.size)
        val hour = data.hourly[0]
        // 14:00Z → 10:00 EDT (UTC-4 on 2026-04-24)
        assertEquals(LocalDateTime.of(2026, 4, 24, 10, 0), hour.time)
        assertEquals(14.0, hour.temperature, 0.0001)
        assertEquals(30, hour.precipitationProbability)
        assertEquals(15.0, hour.windSpeed!!, 0.0001)
        assertEquals(315, hour.windDirection)
        assertEquals(5.0, hour.uvIndex!!, 0.0001)
        assertEquals(WeatherCode.CLEAR_SKY, hour.weatherCode)
    }

    @Test
    fun `riseSet sunrise and sunset project into the location zone`() = runTest {
        val api = mockk<EnvironmentCanadaForecastApi>()
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            features = listOf(
                feature(
                    lat = 45.42,
                    lon = -75.69,
                    riseSet = EcccRiseSet(
                        sunrise = localized("2026-04-24T10:07:00Z"),
                        sunset = localized("2026-04-25T00:01:00Z"),
                    ),
                ),
            ),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val data = adapter.getWeather(45.42, -75.69, null, ZoneId.of("America/Toronto")).getOrThrow()

        // 10:07Z → 06:07 EDT; 00:01Z next day → 20:01 EDT same local day.
        assertEquals("2026-04-24T06:07", data.current.sunrise)
        assertEquals("2026-04-24T20:01", data.current.sunset)
    }

    @Test
    fun `french display language keeps parsing on the english side`() = runTest {
        val adapter = EnvironmentCanadaForecastAdapter(mockk(relaxed = true))
        val data = adapter.mapToWeatherData(
            feature = feature(lat = 45.42, lon = -75.69),
            requestedLat = 45.42,
            requestedLon = -75.69,
            locationName = null,
            zone = ZoneId.of("America/Toronto"),
            language = "fr",
        )

        // Display strings resolve to French…
        assertEquals("Partiellement nuageux", data.current.sourceConditionText)
        assertEquals("Partiellement nuageux", data.daily[0].sourceConditionText)
        // …while day/night pairing and date resolution stayed English-keyed:
        // Friday day+night collapsed into one entry with both temps.
        assertEquals(2, data.daily.size)
        assertEquals(15.0, data.daily[0].temperatureHigh, 0.0001)
        assertEquals(5.0, data.daily[0].temperatureLow, 0.0001)
        assertEquals(LocalDate.of(2026, 4, 24), data.daily[0].date)
        assertEquals(LocalDate.of(2026, 4, 25), data.daily[1].date)
    }

    @Test
    fun `nearest city weighs longitude by cos latitude`() = runTest {
        // At 60°N a degree of longitude is ~half a degree of latitude on the
        // ground. NorthCity is 0.4° of latitude away (~44 km); EastCity is
        // 0.6° of longitude away (~33 km). Raw degree-space distance would
        // pick NorthCity (0.16 < 0.36); ground distance must pick EastCity
        // ((0.6·cos60°)² = 0.09 < 0.16).
        val api = mockk<EnvironmentCanadaForecastApi>()
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            type = "FeatureCollection",
            features = listOf(
                feature(lat = 60.4, lon = -100.0, cityEn = "NorthCity"),
                feature(lat = 60.0, lon = -99.4, cityEn = "EastCity"),
            ),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val data = adapter.getWeather(60.0, -100.0, null).getOrThrow()

        assertEquals("EastCity", data.location.name)
    }

    @Test
    fun `nearest city ignores malformed features without geometry`() = runTest {
        val api = mockk<EnvironmentCanadaForecastApi>()
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            type = "FeatureCollection",
            features = listOf(
                feature(lat = 43.70, lon = -79.40, cityEn = "BrokenCity", geometry = null),
                feature(lat = 43.65, lon = -79.38, cityEn = "Toronto"),
            ),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val data = adapter.getWeather(43.70, -79.40, null).getOrThrow()

        assertEquals("Toronto", data.location.name)
    }

    @Test
    fun `named future first period keeps its actual local date`() = runTest {
        ForecastAdapterTimezoneContract.withDeviceTimeZone("Pacific/Kiritimati") {
            val adapter = EnvironmentCanadaForecastAdapter(mockk(relaxed = true))
            val data = adapter.mapToWeatherData(
                feature = feature(
                    lat = 45.42,
                    lon = -75.69,
                    timestampUtc = "2026-04-23T23:30:00Z",
                    forecasts = listOf(
                        EcccForecastEntry(
                            period = period("Friday", "Friday"),
                            textSummary = localized("Rain. High 9."),
                            temperatures = temperatures("high", 9.0),
                            abbreviatedForecast = EcccAbbreviatedForecast(
                                icon = icon(12),
                                textSummary = localized("Rain"),
                                pop = quantity(80.0),
                            ),
                        ),
                        EcccForecastEntry(
                            period = period("Friday night", "Friday night"),
                            textSummary = localized("Clearing. Low 2."),
                            temperatures = temperatures("low", 2.0),
                            abbreviatedForecast = EcccAbbreviatedForecast(
                                icon = icon(1),
                                textSummary = localized("Clear"),
                            ),
                        ),
                        EcccForecastEntry(
                            period = period("Saturday", "Saturday"),
                            textSummary = localized("Sunny. High 12."),
                            temperatures = temperatures("high", 12.0),
                            abbreviatedForecast = EcccAbbreviatedForecast(
                                icon = icon(1),
                                textSummary = localized("Sunny"),
                            ),
                        ),
                    ),
                ),
                requestedLat = 45.42,
                requestedLon = -75.69,
                locationName = null,
                zone = ZoneId.of("America/Toronto"),
            )

            assertEquals(LocalDate.of(2026, 4, 24), data.daily[0].date)
            assertEquals(LocalDate.of(2026, 4, 25), data.daily[1].date)
            assertEquals(9.0, data.daily[0].temperatureHigh, 0.0001)
            assertEquals(2.0, data.daily[0].temperatureLow, 0.0001)
        }
    }

    @Test
    fun `empty feature collection surfaces a failure instead of crashing`() = runTest {
        val api = mockk<EnvironmentCanadaForecastApi>()
        // Both the tight and the widened bbox queries return nothing.
        coEvery { api.getCityWeather(any(), any(), any(), any()) } returns EcccFeatureCollection(
            features = emptyList(),
        )
        val adapter = EnvironmentCanadaForecastAdapter(api)

        val result = adapter.getWeather(70.0, -90.0, null)
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `icon code maps to a WMO weather code via companion`() {
        // Partly cloudy (iconCode 3) → WMO 2
        val cc = EcccCurrentConditions(iconCode = icon(3), temperature = quantity(10.0))
        assertEquals(2, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(cc))

        // Night-side partly cloudy (iconCode 33 = 3 + 30) should land on the
        // same day-side bucket per the %30 fold.
        val ccNight = EcccCurrentConditions(iconCode = icon(33), temperature = quantity(10.0))
        assertEquals(2, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(ccNight))

        // Rain → 65
        val rain = EcccCurrentConditions(iconCode = icon(12), temperature = quantity(10.0))
        assertEquals(65, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(rain))

        // Thunderstorm → 95
        val thunder = EcccCurrentConditions(iconCode = icon(19), temperature = quantity(10.0))
        assertEquals(95, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(thunder))
    }

    @Test
    fun `condition text fallback fires when icon is absent and reads english`() {
        val ccText = EcccCurrentConditions(
            condition = localized("Freezing rain", "Pluie verglaçante"),
            temperature = quantity(0.0),
        )
        assertEquals(66, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(ccText))

        val ccClear = EcccCurrentConditions(condition = localized("Sunny", "Ensoleillé"), temperature = quantity(20.0))
        assertEquals(0, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(ccClear))
    }

    @Test
    fun `abbreviated forecast icon maps through the same table`() {
        val af = EcccAbbreviatedForecast(
            icon = icon(14),
            textSummary = localized("Heavy snow"),
        )
        // iconCode 14 → 75 (heavy snow)
        assertEquals(75, EnvironmentCanadaForecastAdapter.mapAbbreviatedToWmo(af))
    }

    @Test
    fun `null and malformed icon codes degrade to condition text`() {
        val cc = EcccCurrentConditions(
            condition = localized("Blizzard conditions"),
            iconCode = EcccIcon(value = JsonPrimitive("not-a-number")),
            temperature = quantity(-15.0),
        )
        assertEquals(99, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(cc))

        // A null iconCode + null condition yields clear-sky default (0)
        val unknown = EcccCurrentConditions(temperature = quantity(20.0))
        assertEquals(0, EnvironmentCanadaForecastAdapter.mapCurrentToWmo(unknown))
    }

    @Test
    fun `mapping returns a valid WeatherCode enum even for unknown codes`() {
        val code = EnvironmentCanadaForecastAdapter.mapCurrentToWmo(
            EcccCurrentConditions(condition = localized("Ice fog"), temperature = quantity(-20.0)),
        )
        // Ice fog isn't in the pattern table — falls through to default 0
        // but WeatherCode.fromCode(0) must still resolve to CLEAR_SKY safely.
        assertNotNull(WeatherCode.fromCode(code))
    }

    @Test
    fun `live citypage fixture decodes and maps end to end`() {
        val fixture = requireNotNull(
            javaClass.getResourceAsStream("/eccc_citypage_live.json"),
        ) { "eccc_citypage_live.json missing from test resources" }
            .bufferedReader().use { it.readText() }
        val json = Json { ignoreUnknownKeys = true }
        val collection = json.decodeFromString(EcccFeatureCollection.serializer(), fixture)

        assertEquals(1, collection.features.size)
        val adapter = EnvironmentCanadaForecastAdapter(mockk(relaxed = true))
        val data = adapter.mapToWeatherData(
            feature = collection.features[0],
            requestedLat = 45.4,
            requestedLon = -75.69,
            locationName = null,
            zone = ZoneId.of("America/Toronto"),
            language = "en",
        )

        // Values pinned from the captured live payload (Ottawa, 2026-07-17).
        assertEquals("Ottawa (Kanata - Orléans)", data.location.name)
        assertEquals(15.8, data.current.temperature, 0.0001)
        assertEquals(1017.0, data.current.pressure, 0.01)
        assertEquals(57, data.current.humidity)
        assertEquals(330, data.current.windDirection)
        assertTrue("expected hourly data from hourlyForecastGroup", data.hourly.isNotEmpty())
        assertTrue("expected paired daily data", data.daily.isNotEmpty())
        assertNotNull(data.current.sunrise)
        assertNotNull(data.current.sunset)
        // Every hourly timestamp must be location-local and strictly ascending.
        assertTrue(data.hourly.zipWithNext().all { (a, b) -> a.time < b.time })
    }
}
