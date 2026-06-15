package com.sysadmindoc.nimbus.data.repository

import com.sysadmindoc.nimbus.data.model.MetNorwayResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetNorwayHttpCache @Inject constructor() {

    private val entries = ConcurrentHashMap<String, CacheEntry>()

    fun get(lat: Double, lon: Double): CacheEntry? = entries[coordKey(lat, lon)]

    fun put(lat: Double, lon: Double, entry: CacheEntry) {
        entries[coordKey(lat, lon)] = entry
        if (entries.size > MAX_ENTRIES) evictOldest()
    }

    internal fun coordKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.2f,%.2f", lat, lon)

    private fun evictOldest() {
        val oldest = entries.entries.minByOrNull { it.value.fetchedAt } ?: return
        entries.remove(oldest.key)
    }

    data class CacheEntry(
        val lastModified: String?,
        val expires: Instant?,
        val response: MetNorwayResponse,
        val fetchedAt: Instant = Instant.now(),
    ) {
        fun isFresh(): Boolean {
            val exp = expires ?: return false
            return Instant.now().isBefore(exp)
        }
    }

    companion object {
        private const val MAX_ENTRIES = 50

        private val HTTP_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME
            .withLocale(Locale.US)

        fun parseHttpDate(value: String?): Instant? {
            if (value.isNullOrBlank()) return null
            return try {
                ZonedDateTime.parse(value, HTTP_DATE_FORMAT).toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }
}
