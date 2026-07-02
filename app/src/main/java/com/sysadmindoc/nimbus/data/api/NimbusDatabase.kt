package com.sysadmindoc.nimbus.data.api

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity
import com.sysadmindoc.nimbus.data.model.WeatherDataCacheEntity

@Database(
    entities = [WeatherCacheEntity::class, WeatherDataCacheEntity::class, SavedLocationEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class NimbusDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun savedLocationDao(): SavedLocationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS saved_locations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        region TEXT NOT NULL DEFAULT '',
                        country TEXT NOT NULL DEFAULT '',
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        isCurrentLocation INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                // Match the indices declared on SavedLocationEntity, otherwise Room's
                // schema validator throws IllegalStateException on upgrade from v1.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_saved_locations_isCurrentLocation " +
                        "ON saved_locations(isCurrentLocation)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_saved_locations_sortOrder " +
                        "ON saved_locations(sortOrder)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_locations ADD COLUMN forecastSource TEXT")
                db.execSQL("ALTER TABLE saved_locations ADD COLUMN alertSource TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_locations ADD COLUMN timeZone TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weather_data_cache (
                        cacheKey TEXT NOT NULL,
                        locationKey TEXT NOT NULL,
                        sourceProvider TEXT NOT NULL,
                        savedLocationId INTEGER,
                        schemaVersion INTEGER NOT NULL,
                        payloadJson TEXT NOT NULL,
                        cachedAt INTEGER NOT NULL,
                        PRIMARY KEY(cacheKey)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_weather_data_cache_locationKey " +
                        "ON weather_data_cache(locationKey)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_weather_data_cache_sourceProvider " +
                        "ON weather_data_cache(sourceProvider)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_weather_data_cache_savedLocationId " +
                        "ON weather_data_cache(savedLocationId)"
                )
            }
        }
    }
}
