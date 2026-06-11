package com.sysadmindoc.nimbus.data.repository

import org.junit.Assert.assertEquals
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

internal object ForecastAdapterTimezoneContract {
    suspend fun <T> withDeviceTimeZone(zoneId: String, block: suspend () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        return try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    fun assertProjectedLocalTime(
        label: String,
        instant: Instant,
        zone: ZoneId,
        actual: LocalDateTime?,
    ) {
        assertEquals(label, instant.atZone(zone).toLocalDateTime(), actual)
    }
}
