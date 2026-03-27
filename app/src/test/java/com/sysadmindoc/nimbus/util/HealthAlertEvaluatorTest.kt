package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.model.HourlyConditions
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class HealthAlertEvaluatorTest {

    private fun hourly(
        temperature: Double = 20.0,
        humidity: Int = 50,
        hoursFromNow: Long = 0,
    ) = HourlyConditions(
        time = LocalDateTime.now().plusHours(hoursFromNow),
        temperature = temperature,
        feelsLike = temperature.toDouble(),
        weatherCode = WeatherCode.CLEAR_SKY,
        isDay = true,
        precipitationProbability = 0,
        precipitation = 0.0,
        windSpeed = 5.0,
        windDirection = 180,
        humidity = humidity,
        uvIndex = 3.0,
        cloudCover = 50,
        visibility = 10000.0,
    )

    private fun stableHourly(temp: Double = 20.0, humidity: Int = 50, count: Int = 24): List<HourlyConditions> =
        (0L until count).map { hourly(temperature = temp, humidity = humidity, hoursFromNow = it) }

    // ── Migraine Trigger Tests ──

    @Test
    fun `rapid temperature and humidity change triggers migraine warning`() {
        val data = buildList {
            // Start: 15°C, 40% humidity
            add(hourly(temperature = 15.0, humidity = 40, hoursFromNow = 0))
            add(hourly(temperature = 17.0, humidity = 45, hoursFromNow = 1))
            add(hourly(temperature = 20.0, humidity = 55, hoursFromNow = 2))
            add(hourly(temperature = 22.0, humidity = 65, hoursFromNow = 3))
            add(hourly(temperature = 24.0, humidity = 72, hoursFromNow = 4))
            add(hourly(temperature = 25.0, humidity = 75, hoursFromNow = 5))
            // 10°C temp change, 35% humidity change over 6 hours
        }
        val result = HealthAlertEvaluator.evaluate(data)
        val migraine = result.filter { it.type == HealthAlertType.MIGRAINE_TRIGGER }
        assertTrue("Expected migraine trigger for rapid changes", migraine.isNotEmpty())
        assertTrue(migraine.any { it.severity == HealthSeverity.WARNING })
    }

    @Test
    fun `moderate temperature and humidity change triggers migraine advisory`() {
        val data = buildList {
            add(hourly(temperature = 18.0, humidity = 45, hoursFromNow = 0))
            add(hourly(temperature = 19.5, humidity = 48, hoursFromNow = 1))
            add(hourly(temperature = 21.0, humidity = 52, hoursFromNow = 2))
            add(hourly(temperature = 22.0, humidity = 58, hoursFromNow = 3))
            add(hourly(temperature = 23.0, humidity = 63, hoursFromNow = 4))
            add(hourly(temperature = 23.5, humidity = 66, hoursFromNow = 5))
            // ~5.5°C temp change, ~21% humidity change
        }
        val result = HealthAlertEvaluator.evaluate(data)
        val migraine = result.filter { it.type == HealthAlertType.MIGRAINE_TRIGGER }
        assertTrue("Expected migraine advisory", migraine.isNotEmpty())
    }

    @Test
    fun `stable conditions do not trigger migraine`() {
        val result = HealthAlertEvaluator.evaluate(stableHourly())
        val migraine = result.filter { it.type == HealthAlertType.MIGRAINE_TRIGGER }
        assertTrue("No migraine for stable conditions", migraine.isEmpty())
    }

    // ── Respiratory Tests ──

    @Test
    fun `very high humidity triggers respiratory advisory`() {
        val data = stableHourly(humidity = 90)
        val result = HealthAlertEvaluator.evaluate(data)
        val resp = result.filter { it.type == HealthAlertType.RESPIRATORY }
        assertTrue("Expected respiratory alert for high humidity", resp.isNotEmpty())
    }

    @Test
    fun `very low humidity triggers respiratory advisory`() {
        val data = stableHourly(humidity = 15)
        val result = HealthAlertEvaluator.evaluate(data)
        val resp = result.filter { it.type == HealthAlertType.RESPIRATORY }
        assertTrue("Expected respiratory alert for low humidity", resp.isNotEmpty())
    }

    @Test
    fun `normal humidity does not trigger respiratory`() {
        val data = stableHourly(humidity = 50)
        val result = HealthAlertEvaluator.evaluate(data)
        val resp = result.filter { it.type == HealthAlertType.RESPIRATORY }
        assertTrue(resp.isEmpty())
    }

    // ── Arthritis / Joint Pain Tests ──

    @Test
    fun `large temperature swing triggers arthritis warning`() {
        val data = buildList {
            // 0-11 hours: temperature swings from 5 to 22 (17°C range)
            for (h in 0L..11L) {
                val temp = 5.0 + (h * 1.5)
                add(hourly(temperature = temp, hoursFromNow = h))
            }
        }
        val result = HealthAlertEvaluator.evaluate(data)
        val arthritis = result.filter { it.type == HealthAlertType.ARTHRITIS_TRIGGER }
        assertTrue("Expected arthritis trigger for large temp swing", arthritis.isNotEmpty())
        assertTrue(arthritis.any { it.severity == HealthSeverity.WARNING })
    }

    @Test
    fun `moderate temperature swing triggers arthritis advisory`() {
        val data = buildList {
            for (h in 0L..11L) {
                val temp = 10.0 + (h * 1.0) // 10 to 21 = 11°C range
                add(hourly(temperature = temp, hoursFromNow = h))
            }
        }
        val result = HealthAlertEvaluator.evaluate(data)
        val arthritis = result.filter { it.type == HealthAlertType.ARTHRITIS_TRIGGER }
        assertTrue("Expected arthritis advisory", arthritis.isNotEmpty())
    }

    @Test
    fun `small temperature swing does not trigger arthritis`() {
        val data = stableHourly(count = 12)
        val result = HealthAlertEvaluator.evaluate(data)
        val arthritis = result.filter { it.type == HealthAlertType.ARTHRITIS_TRIGGER }
        assertTrue(arthritis.isEmpty())
    }

    // ── Edge Cases ──

    @Test
    fun `empty hourly list returns no alerts`() {
        val result = HealthAlertEvaluator.evaluate(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `too few entries returns no migraine alerts`() {
        val result = HealthAlertEvaluator.evaluate(listOf(hourly(), hourly(hoursFromNow = 1)))
        val migraine = result.filter { it.type == HealthAlertType.MIGRAINE_TRIGGER }
        assertTrue(migraine.isEmpty())
    }

    @Test
    fun `custom pressure threshold is accepted`() {
        // With a very low threshold, even modest changes should trigger
        val data = buildList {
            add(hourly(temperature = 18.0, humidity = 45, hoursFromNow = 0))
            add(hourly(temperature = 19.0, humidity = 48, hoursFromNow = 1))
            add(hourly(temperature = 20.0, humidity = 50, hoursFromNow = 2))
            add(hourly(temperature = 21.0, humidity = 52, hoursFromNow = 3))
            add(hourly(temperature = 22.0, humidity = 55, hoursFromNow = 4))
            add(hourly(temperature = 24.0, humidity = 67, hoursFromNow = 5))
        }
        // Should not crash with different threshold values
        val result3 = HealthAlertEvaluator.evaluate(data, pressureThresholdHpa = 3.0)
        val result10 = HealthAlertEvaluator.evaluate(data, pressureThresholdHpa = 10.0)
        assertNotNull(result3)
        assertNotNull(result10)
    }

    @Test
    fun `all alert types have messages`() {
        // Trigger all types at once
        val data = buildList {
            add(hourly(temperature = 5.0, humidity = 90, hoursFromNow = 0))
            add(hourly(temperature = 8.0, humidity = 85, hoursFromNow = 1))
            add(hourly(temperature = 12.0, humidity = 72, hoursFromNow = 2))
            add(hourly(temperature = 16.0, humidity = 62, hoursFromNow = 3))
            add(hourly(temperature = 20.0, humidity = 55, hoursFromNow = 4))
            add(hourly(temperature = 22.0, humidity = 50, hoursFromNow = 5))
            add(hourly(temperature = 22.0, humidity = 50, hoursFromNow = 6))
            add(hourly(temperature = 22.0, humidity = 50, hoursFromNow = 7))
            add(hourly(temperature = 22.0, humidity = 50, hoursFromNow = 8))
            add(hourly(temperature = 22.0, humidity = 50, hoursFromNow = 9))
            add(hourly(temperature = 22.0, humidity = 50, hoursFromNow = 10))
            add(hourly(temperature = 22.0, humidity = 50, hoursFromNow = 11))
        }
        val result = HealthAlertEvaluator.evaluate(data)
        result.forEach { alert ->
            assertTrue("Alert message should not be blank", alert.message.isNotBlank())
            assertTrue("Alert type label should not be blank", alert.type.label.isNotBlank())
        }
    }
}
