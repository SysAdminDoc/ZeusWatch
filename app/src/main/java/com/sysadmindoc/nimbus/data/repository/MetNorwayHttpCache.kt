package com.sysadmindoc.nimbus.data.repository

import android.content.Context
import android.util.Log
import com.sysadmindoc.nimbus.data.model.MetNorwayResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locationforecast responses keyed by rounded coordinate, kept in memory and
 * on disk so the minimum cache time MET Norway's terms set still holds after
 * the process dies and the next widget or WorkManager run starts cold.
 */
@Singleton
class MetNorwayHttpCache internal constructor(
    private val dir: File? = null,
    private val clock: () -> Instant = Instant::now,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.cacheDir, DISK_DIR))

    private val entries = ConcurrentHashMap<String, CacheEntry>()

    /** Widgets, workers and the UI can load the same coordinate at once; they share one temp file per key. */
    private val diskLock = Any()

    fun now(): Instant = clock()

    suspend fun get(lat: Double, lon: Double): CacheEntry? {
        val key = coordKey(lat, lon)
        entries[key]?.let { return it }
        val restored = withContext(Dispatchers.IO) { readFromDisk(key) } ?: return null
        return entries.putIfAbsent(key, restored) ?: restored
    }

    suspend fun put(lat: Double, lon: Double, entry: CacheEntry) {
        val key = coordKey(lat, lon)
        entries[key] = entry
        if (entries.size > MAX_ENTRIES) evictLeastFresh()
        // A load cancelled mid-write must still persist, or the next cold start re-asks inside the floor.
        withContext(NonCancellable + Dispatchers.IO) { writeToDisk(key, entry) }
    }

    /**
     * Fresh until the later of the server's `Expires` and the minimum cache
     * time for the area (30 minutes in the Nordic area, 3 hours elsewhere).
     * `Expires` alone is often only minutes out. An entry stamped in the future
     * (the device clock was turned back) counts as stale, so it can't pin an
     * old forecast for as long as the clock was off.
     */
    fun isFresh(entry: CacheEntry, lat: Double, lon: Double): Boolean {
        val now = clock()
        if (entry.fetchedAt.isAfter(now)) return false
        return now.isBefore(freshUntil(entry, lat, lon))
    }

    internal fun freshUntil(entry: CacheEntry, lat: Double, lon: Double): Instant {
        val floor = entry.fetchedAt.plus(minimumCacheTime(lat, lon))
        val expires = entry.expires ?: return floor
        return if (expires.isAfter(floor)) expires else floor
    }

    internal fun coordKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.2f,%.2f", lat, lon)

    /**
     * Drops the entry that goes stale first. The oldest fetch isn't always
     * it: a Nordic entry 20 minutes old is nearly stale while a New York one
     * an hour old still has two hours of floor left.
     */
    private fun evictLeastFresh() {
        val victim = entries.entries.minByOrNull { (key, entry) ->
            keyCoords(key)?.let { (lat, lon) -> freshUntil(entry, lat, lon) } ?: entry.fetchedAt
        } ?: return
        entries.remove(victim.key, victim.value)
    }

    private fun fileFor(key: String): File? = dir?.let { File(it, key.replace(',', '_') + ".json") }

    private fun readFromDisk(key: String): CacheEntry? = synchronized(diskLock) {
        val file = fileFor(key)?.takeIf { it.isFile } ?: return null
        try {
            json.decodeFromString(StoredEntry.serializer(), file.readText()).toEntry()
        } catch (e: IOException) {
            Log.w(TAG, "MET cache read failed for $key: ${e.message}")
            null
        } catch (e: SerializationException) {
            Log.w(TAG, "MET cache entry for $key is unreadable, dropping it: ${e.message}")
            file.delete()
            null
        }
    }

    private fun writeToDisk(key: String, entry: CacheEntry): Unit = synchronized(diskLock) {
        val target = fileFor(key) ?: return
        val parent = target.parentFile ?: return
        try {
            if (!parent.isDirectory && !parent.mkdirs()) return
            val temp = File(parent, target.name + ".tmp")
            temp.writeText(json.encodeToString(StoredEntry.serializer(), StoredEntry.from(entry)))
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) temp.delete()
            }
            trimDisk(parent)
        } catch (e: IOException) {
            Log.w(TAG, "MET cache write failed for $key: ${e.message}")
        }
    }

    /** Same order as [evictLeastFresh], reading the coordinate from the file name and the fetch time from its write time. */
    private fun trimDisk(parent: File) {
        val files = parent.listFiles { file -> file.name.endsWith(".json") } ?: return
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { file ->
            val floor = keyCoords(file.name.removeSuffix(".json").replace('_', ','))
                ?.let { (lat, lon) -> minimumCacheTime(lat, lon) } ?: Duration.ZERO
            file.lastModified() + floor.toMillis()
        }.take(files.size - MAX_ENTRIES).forEach { it.delete() }
    }

    private fun keyCoords(key: String): Pair<Double, Double>? {
        val lat = key.substringBefore(',').toDoubleOrNull() ?: return null
        val lon = key.substringAfter(',', "").toDoubleOrNull() ?: return null
        return lat to lon
    }

    data class CacheEntry(
        val lastModified: String?,
        val expires: Instant?,
        val response: MetNorwayResponse,
        val fetchedAt: Instant = Instant.now(),
    )

    @Serializable
    private data class StoredEntry(
        val lastModified: String? = null,
        val expiresEpochMs: Long? = null,
        val fetchedAtEpochMs: Long,
        val response: MetNorwayResponse,
    ) {
        fun toEntry() = CacheEntry(
            lastModified = lastModified,
            expires = expiresEpochMs?.let(Instant::ofEpochMilli),
            response = response,
            fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMs),
        )

        companion object {
            fun from(entry: CacheEntry) = StoredEntry(
                lastModified = entry.lastModified,
                expiresEpochMs = entry.expires?.toEpochMilli(),
                fetchedAtEpochMs = entry.fetchedAt.toEpochMilli(),
                response = entry.response,
            )
        }
    }

    companion object {
        private const val TAG = "MetNorwayHttpCache"
        private const val DISK_DIR = "met_norway_forecasts"
        private const val MAX_ENTRIES = 50

        /** Half of the update interval, per MET Norway's terms (2026-11-05) and Locationforecast FAQ. */
        internal val NORDIC_MINIMUM_CACHE: Duration = Duration.ofMinutes(30)
        internal val GLOBAL_MINIMUM_CACHE: Duration = Duration.ofHours(3)

        private val json = Json { ignoreUnknownKeys = true }

        private val HTTP_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME
            .withLocale(Locale.US)

        fun minimumCacheTime(lat: Double, lon: Double): Duration =
            if (isMetNordicArea(lat, lon)) NORDIC_MINIMUM_CACHE else GLOBAL_MINIMUM_CACHE

        /**
         * MET doesn't publish the boundary of its "Nordic area", where the
         * forecast updates hourly. These boxes cover Norway, Sweden, Finland,
         * Denmark, the Baltic states and the seas between, and stop short of
         * Germany, Poland, Kaliningrad, Belarus and Russia, their coasts and
         * the water off them. A wrong "no" only costs freshness, while a
         * wrong "yes" breaks MET's terms, so they err inside: Finnish towns
         * east of 28° E below Lapland (Joensuu, Lappeenranta), Kotka,
         * eastern Estonia (Narva), Kirkenes, Gedser, Nida and Svalbard get
         * the 3-hour floor.
         *
         * Every box clears the land and the EEZ (territorial sea included)
         * of Germany, Poland and Russia by at least 2 km, measured on
         * 2026-11-15 against Marine Regions' EEZ polygons and Natural
         * Earth's 1:10m land.
         */
        internal fun isMetNordicArea(lat: Double, lon: Double): Boolean =
            NORDIC_BOXES.any { (lats, lons) -> lat in lats && lon in lons }

        private val NORDIC_BOXES: List<Pair<ClosedFloatingPointRange<Double>, ClosedFloatingPointRange<Double>>> = listOf(
            // Norway, Sweden, western Finland, northern Denmark, Latvia's coast.
            56.0..71.5 to 4.0..24.0,
            // Jutland, the Danish islands and Skåne down to 55.3° N, clear of Germany's North Sea waters.
            55.3..56.0 to 8.0..15.0,
            // Lithuania's coast and Klaipėda, north of the Kaliningrad border on the Curonian Spit.
            55.35..56.0 to 21.0..24.0,
            // Latvia, Estonia west of Lake Peipus, Finland, west of Russia's water off Rodsher.
            55.7..71.5 to 24.0..26.2,
            // Northeast Estonia (Rakvere, Kunda), south of that water.
            55.7..59.8 to 26.2..26.5,
            // Loviisa and the Finnish coast north of it.
            60.15..71.5 to 26.2..26.5,
            // Eastern Finland north of the Gulf of Finland's Russian shore.
            61.0..71.5 to 26.5..28.0,
            // Varanger peninsula (Vadsø), clear of the water off Russia's Rybachy peninsula.
            70.0..71.5 to 28.0..31.0,
            // Vardø, north of where Russia's water reaches 70° N.
            70.2..71.5 to 31.0..31.2,
            // Estonia and Latvia up to the Russian border (Tartu, Võru), south of Russia's Gulf of Finland islands.
            56.2..59.6 to 26.5..27.2,
            // Western Ærø, north of the Kiel Bight's German water.
            54.8..55.3 to 10.1..10.3,
            // Eastern Ærø (Marstal) and Langeland.
            54.75..55.3 to 10.3..11.0,
            // Western Lolland, north of the German water off Fehmarn.
            54.66..55.3 to 11.0..11.25,
            // Lolland (Rødby) and Falster, north of the Fehmarn Belt line.
            54.62..55.3 to 11.25..12.3,
            // Møn and Stevns, north of the water off Germany's Darss coast.
            54.9..55.3 to 12.3..12.7,
            // Bornholm and Christiansø.
            54.98..55.33 to 14.65..15.2,
            // Sønderborg and western Als, north of the German shore at Holnis.
            54.9..55.3 to 9.65..9.85,
            // Eastern and southern Als down to Kegnæs's neck, north of the German water off Gelting.
            54.86..55.3 to 9.85..10.1,
            // Gråsten, north of the German water off Holnis.
            54.91..55.3 to 9.5..9.65,
            // Aabenraa and the land north of the inner Flensburg Fjord.
            54.87..55.3 to 9.3..9.5,
            // Western Rømø (Lakolk), north of Sylt's water.
            55.08..55.3 to 8.45..8.53,
            // Eastern Rømø (Havneby) and the causeway, north and east of Sylt's water.
            55.04..55.3 to 8.53..8.62,
            // Southwest Jutland north of the land border, east of the German Wadden Sea.
            54.94..55.3 to 8.62..9.3,
        )

        /**
         * `Expires` measured against the server's `Date` and laid on the
         * device's own fetch time, so a phone clock that's hours off doesn't
         * stretch or shrink it. Without a `Date` header it's used as sent.
         */
        fun expiresAt(expiresHeader: String?, dateHeader: String?, fetchedAt: Instant): Instant? {
            val expires = parseHttpDate(expiresHeader) ?: return null
            val serverDate = parseHttpDate(dateHeader) ?: return expires
            return fetchedAt.plus(Duration.between(serverDate, expires))
        }

        fun parseHttpDate(value: String?): Instant? {
            if (value.isNullOrBlank()) return null
            return try {
                ZonedDateTime.parse(value, HTTP_DATE_FORMAT).toInstant()
            } catch (ignored: Exception) {
                null
            }
        }
    }
}
