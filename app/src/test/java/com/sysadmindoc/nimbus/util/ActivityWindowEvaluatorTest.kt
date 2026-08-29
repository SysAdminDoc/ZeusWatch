package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The window evaluator answers "when should I go", so the thing that matters
 * most is that it declines to answer when it does not know.
 */
class ActivityWindowEvaluatorTest {

    private val start = LocalDateTime.of(2026, 6, 1, 6, 0)

    private fun hour(
        index: Int,
        temperature: Double = 20.0,
        precipitationProbability: Int = 0,
        windSpeed: Double? = 10.0,
        uvIndex: Double? = 3.0,
        humidity: Int? = 50,
        cloudCover: Int? = 10,
    ) = HourlyConditions(
        time = start.plusHours(index.toLong()),
        temperature = temperature,
        feelsLike = temperature,
        weatherCode = WeatherCode.CLEAR_SKY,
        isDay = true,
        precipitationProbability = precipitationProbability,
        precipitation = null,
        windSpeed = windSpeed,
        windDirection = 180,
        humidity = humidity,
        uvIndex = uvIndex,
        cloudCover = cloudCover,
        visibility = 16000.0,
    )

    private fun windowFor(
        hours: List<HourlyConditions>,
        type: ActivityType = ActivityType.RUNNING,
        thresholds: ActivityThresholds = ActivityThresholds(),
        aqi: Map<LocalDateTime, Int> = emptyMap(),
    ): ActivityWindow = ActivityWindowEvaluator
        .evaluate(hours, thresholds, aqi, listOf(type))
        .single()

    @Test
    fun `a clear day is one long window`() {
        val window = windowFor(List(24) { hour(it) })

        assertTrue(window.hasWindow)
        assertEquals(start, window.start)
        assertEquals(start.plusHours(23), window.end)
        assertEquals(24, window.hourCount)
    }

    @Test
    fun `rain in the middle splits the day and the longer side wins`() {
        val hours = List(24) { index ->
            // Hours 6 to 9 are washed out; the afternoon is the longer stretch.
            if (index in 6..9) hour(index, precipitationProbability = 95) else hour(index)
        }

        val window = windowFor(hours)

        assertEquals(start.plusHours(10), window.start)
        assertEquals(start.plusHours(23), window.end)
    }

    @Test
    fun `a day with nothing good reports no window rather than the least bad hour`() {
        // Every hour is freezing and pouring. Recommending the driest hour of a
        // washout is worse than saying there isn't a good time.
        val hours = List(24) { hour(it, temperature = -15.0, precipitationProbability = 100) }

        val window = windowFor(hours)

        assertFalse(window.hasWindow)
        assertEquals(null, window.start)
        assertTrue("expected the blockers to be named", window.limitingFactors.isNotEmpty())
    }

    @Test
    fun `the limiting factor names what actually held the window back`() {
        // Comfortable except for a steady wind, so wind is the answer and
        // temperature is not mentioned.
        val hours = List(24) { hour(it, windSpeed = 45.0) }

        val window = windowFor(hours, type = ActivityType.CYCLING)

        assertTrue(ActivityFactor.WIND in window.limitingFactors)
        assertFalse(ActivityFactor.TEMPERATURE in window.limitingFactors)
    }

    @Test
    fun `a window where everything is fine names no limiting factor`() {
        val window = windowFor(List(24) { hour(it) })

        assertEquals(emptyList<ActivityFactor>(), window.limitingFactors)
    }

    @Test
    fun `missing air quality lowers confidence instead of being scored as good`() {
        // The current-conditions evaluator scores an absent AQI as 80, which is
        // a guess presented as a measurement. Running cares about air quality,
        // so its absence has to show up as less certainty.
        val hours = List(24) { hour(it) }

        val withAqi = windowFor(hours, aqi = hours.associate { it.time to 20 })
        val withoutAqi = windowFor(hours)

        assertEquals(ActivityWindowConfidence.HIGH, withAqi.confidence)
        assertTrue(withoutAqi.confidence < withAqi.confidence)
    }

    @Test
    fun `missing air quality does not drag the score down either`() {
        // The other half: an absent factor must not be scored as zero, or a
        // location with no air-quality provider would never have a good day.
        val hours = List(24) { hour(it) }

        val withGoodAqi = windowFor(hours, aqi = hours.associate { it.time to 10 })
        val withoutAqi = windowFor(hours)

        assertTrue(withoutAqi.hasWindow)
        assertEquals(withGoodAqi.hourCount, withoutAqi.hourCount)
    }

    @Test
    fun `a short forecast is low confidence however clean it is`() {
        val window = windowFor(List(6) { hour(it) })

        assertEquals(ActivityWindowConfidence.LOW, window.confidence)
        assertTrue(window.hasWindow)
    }

    @Test
    fun `an empty forecast reports nothing at all`() {
        val window = windowFor(emptyList())

        assertFalse(window.hasWindow)
        assertEquals(ActivityWindowConfidence.NONE, window.confidence)
    }

    @Test
    fun `hours beyond the horizon are ignored`() {
        // 48 hours of data, and the good stretch is on day two. Planning
        // tomorrow evening is not what this card is for.
        val hours = List(48) { index ->
            if (index >= 24) hour(index) else hour(index, precipitationProbability = 100)
        }

        val window = windowFor(hours)

        assertFalse(window.hasWindow)
    }

    @Test
    fun `raising the temperature ceiling opens a window a strict setting closes`() {
        val hours = List(24) { hour(it, temperature = 33.0) }

        val strict = windowFor(hours)
        val relaxed = windowFor(hours, thresholds = ActivityThresholds(maxComfortableTempC = 35.0))

        assertFalse(strict.hasWindow)
        assertTrue(relaxed.hasWindow)
    }

    @Test
    fun `stargazing is judged on cloud rather than temperature`() {
        val overcast = List(24) { hour(it, cloudCover = 95) }
        val clear = List(24) { hour(it, cloudCover = 5) }

        assertFalse(windowFor(overcast, type = ActivityType.STARGAZING).hasWindow)
        assertTrue(windowFor(clear, type = ActivityType.STARGAZING).hasWindow)
    }

    @Test
    fun `every activity gets an answer`() {
        val windows = ActivityWindowEvaluator.evaluate(List(24) { hour(it) })

        assertEquals(ActivityType.entries.size, windows.size)
        assertEquals(ActivityType.entries.toSet(), windows.map { it.type }.toSet())
    }

    @Test
    fun `an hour missing every optional reading still scores on what is there`() {
        // A provider that returns only temperature and precipitation must not
        // produce a zero score, which would read as "terrible" rather than
        // "we only know two things".
        val hours = List(24) {
            hour(it, windSpeed = null, uvIndex = null, humidity = null, cloudCover = null)
        }

        val window = windowFor(hours)

        assertTrue(window.hasWindow)
        assertEquals(ActivityWindowConfidence.LOW, window.confidence)
    }
}
