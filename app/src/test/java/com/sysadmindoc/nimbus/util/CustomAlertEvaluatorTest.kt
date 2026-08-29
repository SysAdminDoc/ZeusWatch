package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.AirQualityData
import com.sysadmindoc.nimbus.data.model.AqiLevel
import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.CustomAlertMetric
import com.sysadmindoc.nimbus.data.model.CustomAlertOperator
import com.sysadmindoc.nimbus.data.model.CustomAlertRule
import com.sysadmindoc.nimbus.data.model.CustomAlertUnit
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.LocationInfo
import com.sysadmindoc.nimbus.data.model.PollenData
import com.sysadmindoc.nimbus.data.model.PollenReading
import com.sysadmindoc.nimbus.data.model.PollenThresholdsDb
import com.sysadmindoc.nimbus.data.model.WeatherData
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CustomAlertEvaluatorTest {

    private val baseTime = LocalDateTime.of(2026, 1, 15, 9, 0)

    @Test
    fun `snowfall rule compares in millimeters (cm forecast x10)`() {
        // Open-Meteo snowfall is centimeters. 3 cm total = 30 mm, which must
        // exceed a 20 mm threshold. The pre-fix bug summed raw cm (3) and never
        // fired.
        val rule = snowfallRule(thresholdMm = 20.0)
        val data = weatherWithHourlySnowfallCm(listOf(1.0, 1.0, 1.0)) // 3 cm total

        val triggered = evaluateCustomAlertRules(listOf(rule), data)

        assertEquals(1, triggered.size)
        assertEquals(rule.id, triggered.first().rule.id)
        // Observed value is reported in canonical mm (30.0), not raw cm.
        assertEquals(30.0, triggered.first().observedCanonical, 0.001)
    }

    @Test
    fun `snowfall rule below threshold does not fire`() {
        val rule = snowfallRule(thresholdMm = 20.0)
        val data = weatherWithHourlySnowfallCm(listOf(1.0, 0.5)) // 1.5 cm = 15 mm

        assertTrue(evaluateCustomAlertRules(listOf(rule), data).isEmpty())
    }

    private fun snowfallRule(thresholdMm: Double) = CustomAlertRule(
        id = "snow-1",
        metric = CustomAlertMetric.SNOWFALL_SUM_NEXT_24H,
        operator = CustomAlertOperator.GREATER_THAN,
        thresholdCanonical = thresholdMm,
        enabled = true,
    )

    @Test
    fun `a ragweed rule fires on the peak count for that pollen only`() {
        val rule = pollenRule(CustomAlertMetric.POLLEN_RAGWEED_PEAK_TODAY, threshold = 20.0)
        val airQuality = airQualityWith(ragweed = 45.0, grass = 2.0)

        val triggered = evaluateCustomAlertRules(listOf(rule), weather(), airQuality)

        assertEquals(1, triggered.size)
        assertEquals(45.0, triggered.single().observedCanonical, 0.001)
    }

    @Test
    fun `a rule for one pollen ignores a spike in another`() {
        val rule = pollenRule(CustomAlertMetric.POLLEN_GRASS_PEAK_TODAY, threshold = 20.0)
        val airQuality = airQualityWith(ragweed = 200.0, grass = 3.0)

        // An allergy is to a specific pollen; a birch spike is no reason to
        // wake a grass-sensitive user.
        assertTrue(evaluateCustomAlertRules(listOf(rule), weather(), airQuality).isEmpty())
    }

    @Test
    fun `a pollen rule no-ops where the location has no pollen coverage`() {
        val above = pollenRule(CustomAlertMetric.POLLEN_BIRCH_PEAK_TODAY, threshold = 20.0)
        val below = pollenRule(
            CustomAlertMetric.POLLEN_BIRCH_PEAK_TODAY,
            threshold = 20.0,
            operator = CustomAlertOperator.LESS_THAN,
        )

        // PollenReading.NONE means "no data", not "zero grains". Reading it as
        // 0.0 would fire the below-threshold rule every single day in every
        // region Open-Meteo has no pollen model for.
        val airQuality = airQualityWith()

        assertTrue(evaluateCustomAlertRules(listOf(above, below), weather(), airQuality).isEmpty())
    }

    @Test
    fun `a real zero reading is honoured, unlike absent data`() {
        val below = pollenRule(
            CustomAlertMetric.POLLEN_GRASS_PEAK_TODAY,
            threshold = 5.0,
            operator = CustomAlertOperator.LESS_THAN,
        )

        // Zero grains reported is a real measurement and must fire a
        // below-threshold rule; no reading at all must not.
        val reported = evaluateCustomAlertRules(listOf(below), weather(), airQualityWith(grass = 0.0))
        val absent = evaluateCustomAlertRules(listOf(below), weather(), airQualityWith())

        assertEquals(1, reported.size)
        assertTrue(absent.isEmpty())
    }

    @Test
    fun `a pollen rule no-ops when air quality was never fetched`() {
        val rule = pollenRule(CustomAlertMetric.POLLEN_OLIVE_PEAK_TODAY, threshold = 5.0)

        assertTrue(evaluateCustomAlertRules(listOf(rule), weather(), airQuality = null).isEmpty())
    }

    @Test
    fun `every pollen metric asks the worker to fetch air quality`() {
        val pollenMetrics = CustomAlertMetric.entries.filter { it.unit == CustomAlertUnit.POLLEN }

        assertEquals(6, pollenMetrics.size)
        // Derived from the unit, so a seventh pollen type cannot be added
        // without the worker learning to fetch for it.
        assertTrue(pollenMetrics.all { it.requiresAirQuality })
        assertTrue(CustomAlertMetric.AQI_NOW.requiresAirQuality)
        assertTrue(CustomAlertMetric.entries.none { it.unit == CustomAlertUnit.CELSIUS && it.requiresAirQuality })
    }

    private fun pollenRule(
        metric: CustomAlertMetric,
        threshold: Double,
        operator: CustomAlertOperator = CustomAlertOperator.GREATER_THAN,
    ) = CustomAlertRule(
        id = "pollen-rule",
        metric = metric,
        operator = operator,
        thresholdCanonical = threshold,
        enabled = true,
    )

    /**
     * Builds readings through [PollenReading.fromConcentration] exactly as
     * AirQualityRepository does. Hand-constructing PollenReading.NONE instead
     * is what let a dead no-data guard ship: the repository's readings carry a
     * name and never equalled the nameless sentinel.
     */
    private fun airQualityWith(
        grass: Double? = null,
        birch: Double? = null,
        ragweed: Double? = null,
        olive: Double? = null,
        alder: Double? = null,
        mugwort: Double? = null,
    ) = AirQualityData(
        usAqi = 40,
        europeanAqi = 30,
        aqiLevel = AqiLevel.GOOD,
        pm25 = 5.0,
        pm10 = 8.0,
        ozone = 30.0,
        nitrogenDioxide = 10.0,
        sulphurDioxide = 1.0,
        carbonMonoxide = 100.0,
        pollen = PollenData(),
        pollenPeakToday = PollenData(
            alder = PollenReading.fromConcentration(alder, "Alder", PollenThresholdsDb.ALDER),
            birch = PollenReading.fromConcentration(birch, "Birch", PollenThresholdsDb.BIRCH),
            grass = PollenReading.fromConcentration(grass, "Grass", PollenThresholdsDb.GRASS),
            mugwort = PollenReading.fromConcentration(mugwort, "Mugwort", PollenThresholdsDb.MUGWORT),
            olive = PollenReading.fromConcentration(olive, "Olive", PollenThresholdsDb.OLIVE),
            ragweed = PollenReading.fromConcentration(ragweed, "Ragweed", PollenThresholdsDb.RAGWEED),
        ),
    )

    private fun weather() = WeatherData(
        location = LocationInfo(name = "Test", latitude = 40.0, longitude = -74.0),
        current = current(),
        hourly = emptyList(),
        daily = emptyList(),
    )

    private fun weatherWithHourlySnowfallCm(snowfallCm: List<Double>): WeatherData {
        val hourly = snowfallCm.mapIndexed { i, cm ->
            HourlyConditions(
                time = baseTime.plusHours(i.toLong()),
                temperature = 0.0,
                feelsLike = null,
                weatherCode = WeatherCode.SNOW_SLIGHT,
                isDay = true,
                precipitationProbability = 100,
                precipitation = null,
                windSpeed = null,
                windDirection = null,
                humidity = null,
                uvIndex = null,
                cloudCover = null,
                visibility = null,
                snowfall = cm,
            )
        }
        return WeatherData(
            location = LocationInfo(name = "Test", latitude = 40.0, longitude = -74.0),
            current = current(),
            hourly = hourly,
            daily = emptyList(),
        )
    }

    private fun current() = CurrentConditions(
        temperature = 0.0,
        feelsLike = 0.0,
        humidity = 90,
        weatherCode = WeatherCode.SNOW_SLIGHT,
        isDay = true,
        windSpeed = 0.0,
        windDirection = 0,
        windGusts = null,
        pressure = 1000.0,
        uvIndex = 0.0,
        visibility = null,
        dewPoint = null,
        cloudCover = 100,
        precipitation = 0.0,
        dailyHigh = 1.0,
        dailyLow = -5.0,
        sunrise = null,
        sunset = null,
    )
}
