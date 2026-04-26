package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.CurrentConditions
import com.sysadmindoc.nimbus.data.model.DailyConditions
import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.SummaryStyle
import com.sysadmindoc.nimbus.data.repository.TempUnit
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

// ── Fixture helpers ────────────────────────────────────────────────────────────

private val CELSIUS = NimbusSettings(tempUnit = TempUnit.CELSIUS)

private fun current(
    temperature: Double = 20.0,
    feelsLike: Double = 20.0,
    humidity: Int = 50,
    weatherCode: WeatherCode = WeatherCode.CLEAR_SKY,
    observationTime: LocalDateTime? = LocalDateTime.of(2024, 6, 15, 14, 0),
    isDay: Boolean = true,
    windSpeed: Double = 10.0,
    windDirection: Int = 180,
    pressure: Double = 1013.0,
    uvIndex: Double = 3.0,
    dailyHigh: Double = 25.0,
    dailyLow: Double = 15.0,
): CurrentConditions = CurrentConditions(
    temperature = temperature,
    feelsLike = feelsLike,
    humidity = humidity,
    weatherCode = weatherCode,
    observationTime = observationTime,
    isDay = isDay,
    windSpeed = windSpeed,
    windDirection = windDirection,
    windGusts = null,
    pressure = pressure,
    uvIndex = uvIndex,
    visibility = 10_000.0,
    dewPoint = null,
    cloudCover = 0,
    precipitation = 0.0,
    dailyHigh = dailyHigh,
    dailyLow = dailyLow,
    sunrise = null,
    sunset = null,
)

private fun daily(
    temperatureHigh: Double = 25.0,
    temperatureLow: Double = 15.0,
): DailyConditions = DailyConditions(
    date = LocalDate.of(2024, 6, 15),
    weatherCode = WeatherCode.CLEAR_SKY,
    temperatureHigh = temperatureHigh,
    temperatureLow = temperatureLow,
    precipitationProbability = 0,
    precipitationSum = null,
    sunrise = null,
    sunset = null,
    uvIndexMax = null,
    windSpeedMax = null,
    windDirectionDominant = null,
)

/** Build a list of 12 hourly slots with the given precipitation probabilities. */
private fun hourly(vararg precipProbabilities: Int): List<HourlyConditions> {
    return precipProbabilities.take(12).mapIndexed { i, prob ->
        HourlyConditions(
            time = LocalDateTime.of(2024, 6, 15, i, 0),
            temperature = 20.0,
            feelsLike = null,
            weatherCode = WeatherCode.CLEAR_SKY,
            isDay = true,
            precipitationProbability = prob,
            precipitation = null,
            windSpeed = null,
            windDirection = null,
            humidity = null,
            uvIndex = null,
            cloudCover = null,
            visibility = null,
        )
    }
}

// ── generate() tests ───────────────────────────────────────────────────────────

class WeatherSummaryEngineTest {

    // ── Time-of-day phrasing ────────────────────────────────────────────────

    @Test
    fun `generate uses 'this morning' for daytime hours before noon`() {
        val c = current(observationTime = LocalDateTime.of(2024, 6, 15, 9, 0))
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'this morning' in: $result", result.contains("this morning"))
    }

    @Test
    fun `generate uses 'this afternoon' for hours 12-16`() {
        val c = current(observationTime = LocalDateTime.of(2024, 6, 15, 14, 0))
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'this afternoon' in: $result", result.contains("this afternoon"))
    }

    @Test
    fun `generate uses 'this evening' for hours 17 and beyond`() {
        val c = current(observationTime = LocalDateTime.of(2024, 6, 15, 18, 0))
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'this evening' in: $result", result.contains("this evening"))
    }

    @Test
    fun `generate uses 'tonight' when isDay is false`() {
        val c = current(isDay = false, observationTime = LocalDateTime.of(2024, 6, 15, 22, 0))
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'tonight' in: $result", result.contains("tonight"))
    }

    // ── Condition phrases ───────────────────────────────────────────────────

    @Test
    fun `generate opens with correct phrase for CLEAR_SKY daytime`() {
        val c = current(weatherCode = WeatherCode.CLEAR_SKY)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue(result.startsWith("Clear skies"))
    }

    @Test
    fun `generate opens with correct phrase for RAIN_MODERATE`() {
        val c = current(weatherCode = WeatherCode.RAIN_MODERATE)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'Rainy' in: $result", result.startsWith("Rainy"))
    }

    @Test
    fun `generate opens with correct phrase for THUNDERSTORM`() {
        val c = current(weatherCode = WeatherCode.THUNDERSTORM)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue(result.startsWith("Thunderstorms"))
    }

    @Test
    fun `generate opens with correct phrase for SNOW_HEAVY`() {
        val c = current(weatherCode = WeatherCode.SNOW_HEAVY)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue(result.startsWith("Heavy snow"))
    }

    @Test
    fun `generate opens with correct phrase for PARTLY_CLOUDY`() {
        val c = current(weatherCode = WeatherCode.PARTLY_CLOUDY)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue(result.startsWith("Partly cloudy"))
    }

    // ── Temperature sentence ────────────────────────────────────────────────

    @Test
    fun `generate produces Highs sentence using daily temperatureHigh`() {
        val c = current()
        val d = daily(temperatureHigh = 25.0)
        val result = WeatherSummaryEngine.generate(c, d, emptyList(), s = CELSIUS)
        assertTrue("Expected 'Highs near 25°' in: $result", result.contains("Highs near 25°"))
    }

    @Test
    fun `generate produces Lows sentence at night using daily temperatureLow`() {
        val c = current(isDay = false, observationTime = LocalDateTime.of(2024, 6, 15, 23, 0))
        val d = daily(temperatureLow = 10.0)
        val result = WeatherSummaryEngine.generate(c, d, emptyList(), s = CELSIUS)
        assertTrue("Expected 'Lows near 10°' in: $result", result.contains("Lows near 10°"))
    }

    // ── Wind notes ──────────────────────────────────────────────────────────

    @Test
    fun `generate omits wind note below 30 km-h`() {
        val c = current(windSpeed = 29.9)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertFalse(result.contains("wind"))
    }

    @Test
    fun `generate emits moderate winds between 30 and 40 km-h`() {
        val c = current(windSpeed = 35.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'moderate winds' in: $result", result.contains("moderate winds"))
    }

    @Test
    fun `generate emits strong winds between 40 and 60 km-h`() {
        val c = current(windSpeed = 50.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'strong winds' in: $result", result.contains("strong winds"))
    }

    @Test
    fun `generate emits gale-force winds above 60 km-h`() {
        val c = current(windSpeed = 65.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue("Expected 'gale-force winds' in: $result", result.contains("gale-force winds"))
    }

    // ── UV warnings ─────────────────────────────────────────────────────────

    @Test
    fun `generate emits very high UV warning for uvIndex 8+`() {
        val c = current(uvIndex = 9.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue(result.contains("UV index is very high"))
        assertFalse(result.contains("UV index is high") && !result.contains("very"))
    }

    @Test
    fun `generate emits high UV warning for uvIndex 6 to 7`() {
        val c = current(uvIndex = 7.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue(result.contains("UV index is high"))
        assertFalse(result.contains("very"))
    }

    @Test
    fun `generate suppresses UV warnings at night`() {
        val c = current(isDay = false, uvIndex = 10.0, observationTime = LocalDateTime.of(2024, 6, 15, 22, 0))
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertFalse("UV warning should be suppressed at night", result.contains("UV"))
    }

    // ── Humidity note ───────────────────────────────────────────────────────

    @Test
    fun `generate emits muggy note when humidity high and temperature hot`() {
        val c = current(humidity = 85, temperature = 30.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertTrue(result.contains("muggy"))
    }

    @Test
    fun `generate omits muggy note when temperature not hot enough`() {
        val c = current(humidity = 85, temperature = 24.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertFalse(result.contains("muggy"))
    }

    @Test
    fun `generate omits muggy note when humidity not high enough`() {
        val c = current(humidity = 70, temperature = 30.0)
        val result = WeatherSummaryEngine.generate(c, daily(), emptyList(), s = CELSIUS)
        assertFalse(result.contains("muggy"))
    }

    // ── Yesterday comparison ────────────────────────────────────────────────

    @Test
    fun `generate appends warmer-than-yesterday when diff greater than 2`() {
        // today 25°C, yesterday 18°C → diff = 7 in Celsius
        val result = WeatherSummaryEngine.generate(
            current(), daily(temperatureHigh = 25.0), emptyList(),
            yesterdayHigh = 18.0, s = CELSIUS,
        )
        assertTrue("Expected 'warmer than yesterday' in: $result", result.contains("warmer than yesterday"))
    }

    @Test
    fun `generate appends cooler-than-yesterday when diff less than minus 2`() {
        // today 20°C, yesterday 26°C → diff = -6
        val result = WeatherSummaryEngine.generate(
            current(), daily(temperatureHigh = 20.0), emptyList(),
            yesterdayHigh = 26.0, s = CELSIUS,
        )
        assertTrue("Expected 'cooler than yesterday' in: $result", result.contains("cooler than yesterday"))
    }

    @Test
    fun `generate omits comparison when diff is within 2 degrees`() {
        // today 25°C, yesterday 24°C → diff = 1
        val result = WeatherSummaryEngine.generate(
            current(), daily(temperatureHigh = 25.0), emptyList(),
            yesterdayHigh = 24.0, s = CELSIUS,
        )
        assertFalse(result.contains("yesterday"))
    }

    @Test
    fun `generate omits comparison when yesterdayHigh is null`() {
        val result = WeatherSummaryEngine.generate(
            current(), daily(), emptyList(),
            yesterdayHigh = null, s = CELSIUS,
        )
        assertFalse(result.contains("yesterday"))
    }
}

// ── precipitationOutlook tests (via generate) ──────────────────────────────────

class WeatherSummaryEnginePrecipTest {

    @Test
    fun `precipitationOutlook returns null when hourly is empty`() {
        val result = WeatherSummaryEngine.generate(current(), daily(), emptyList(), s = CELSIUS)
        assertFalse(result.contains("rain"))
    }

    @Test
    fun `precipitationOutlook returns null when maxProb below 20`() {
        val h = hourly(*IntArray(12) { 10 })
        val result = WeatherSummaryEngine.generate(current(), daily(), h, s = CELSIUS)
        assertFalse(result.contains("rain"))
    }

    @Test
    fun `precipitationOutlook produces throughout-the-day for 8 or more rainy hours`() {
        // 12 hours all above 40
        val h = hourly(*IntArray(12) { 70 })
        val result = WeatherSummaryEngine.generate(current(), daily(), h, s = CELSIUS)
        assertTrue(result.contains("throughout the day"))
    }

    @Test
    fun `precipitationOutlook produces rain-expected-soon for 4+ rainy hours starting early`() {
        // firstRainIdx = 0, rainyHours = 4
        val probs = IntArray(12) { i -> if (i < 4) 70 else 10 }
        val h = hourly(*probs)
        val result = WeatherSummaryEngine.generate(current(), daily(), h, s = CELSIUS)
        assertTrue("Expected 'rain expected soon' in: $result", result.contains("rain expected soon"))
    }

    @Test
    fun `precipitationOutlook produces rain-developing-later for 4+ rainy hours starting after index 6`() {
        // firstRainIdx = 7, rainyHours = 5
        val probs = IntArray(12) { i -> if (i >= 7) 70 else 10 }
        val h = hourly(*probs)
        val result = WeatherSummaryEngine.generate(current(), daily(), h, s = CELSIUS)
        assertTrue("Expected 'developing later' in: $result", result.contains("developing later"))
    }

    @Test
    fun `precipitationOutlook produces rain-likely-later-today for 4+ rainy hours in mid-period`() {
        // firstRainIdx = 4 (>2, <=6), rainyHours = 4
        val probs = IntArray(12) { i -> if (i in 4..7) 70 else 10 }
        val h = hourly(*probs)
        val result = WeatherSummaryEngine.generate(current(), daily(), h, s = CELSIUS)
        assertTrue("Expected 'rain likely later today' in: $result", result.contains("rain likely later today"))
    }

    @Test
    fun `precipitationOutlook produces chance-of-showers for 1 rainy hour with maxProb above 60`() {
        // 1 rainy hour at 70%, rest at 10%
        val probs = IntArray(12) { i -> if (i == 5) 70 else 10 }
        val h = hourly(*probs)
        val result = WeatherSummaryEngine.generate(current(), daily(), h, s = CELSIUS)
        assertTrue("Expected 'chance of showers' in: $result", result.contains("chance of showers"))
    }

    @Test
    fun `precipitationOutlook produces slight-chance-of-rain for 1 rainy hour with maxProb at or below 60`() {
        // 1 rainy hour at 50%, rest at 10%
        val probs = IntArray(12) { i -> if (i == 5) 50 else 10 }
        val h = hourly(*probs)
        val result = WeatherSummaryEngine.generate(current(), daily(), h, s = CELSIUS)
        assertTrue("Expected 'slight chance of rain' in: $result", result.contains("slight chance of rain"))
    }
}

// ── generateWithStyle() tests ──────────────────────────────────────────────────

class WeatherSummaryEngineWithStyleTest {

    @Test
    fun `generateWithStyle returns template when style is TEMPLATE`() = runTest {
        val s = CELSIUS.copy(summaryStyle = SummaryStyle.TEMPLATE)
        val aiEngine = mockk<SummaryEngine>(relaxed = true)
        val result = WeatherSummaryEngine.generateWithStyle(current(), daily(), emptyList(), s = s, aiEngine = aiEngine)
        // Template output always starts with a capitalised condition phrase
        assertTrue("Expected a non-blank template result", result.isNotBlank())
        assertTrue("Expected 'Clear skies' at start: $result", result.startsWith("Clear skies"))
    }

    @Test
    fun `generateWithStyle returns template when aiEngine is null even for AI_GENERATED style`() = runTest {
        val s = CELSIUS.copy(summaryStyle = SummaryStyle.AI_GENERATED)
        val result = WeatherSummaryEngine.generateWithStyle(current(), daily(), emptyList(), s = s, aiEngine = null)
        assertTrue("Expected 'Clear skies' at start: $result", result.startsWith("Clear skies"))
    }

    @Test
    fun `generateWithStyle returns AI text when engine generates successfully`() = runTest {
        val s = CELSIUS.copy(summaryStyle = SummaryStyle.AI_GENERATED)
        val engine = mockk<SummaryEngine>()
        coEvery {
            engine.generate(any(), any(), any(), any(), any(), any(), any(), any())
        } returns "Sunny with a high of 25°C."
        val result = WeatherSummaryEngine.generateWithStyle(current(), daily(), emptyList(), s = s, aiEngine = engine)
        assertTrue("Expected AI text in result: $result", result.contains("Sunny with a high"))
    }

    @Test
    fun `generateWithStyle falls back to template when AI engine returns null`() = runTest {
        val s = CELSIUS.copy(summaryStyle = SummaryStyle.AI_GENERATED)
        val engine = mockk<SummaryEngine>()
        coEvery {
            engine.generate(any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        val result = WeatherSummaryEngine.generateWithStyle(current(), daily(), emptyList(), s = s, aiEngine = engine)
        assertTrue("Expected template fallback starting 'Clear skies': $result", result.startsWith("Clear skies"))
    }

    @Test
    fun `generateWithStyle falls back to template when AI engine throws`() = runTest {
        val s = CELSIUS.copy(summaryStyle = SummaryStyle.AI_GENERATED)
        val engine = mockk<SummaryEngine>()
        coEvery {
            engine.generate(any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("Model load failed")
        val result = WeatherSummaryEngine.generateWithStyle(current(), daily(), emptyList(), s = s, aiEngine = engine)
        assertTrue("Expected template fallback starting 'Clear skies': $result", result.startsWith("Clear skies"))
    }
}
