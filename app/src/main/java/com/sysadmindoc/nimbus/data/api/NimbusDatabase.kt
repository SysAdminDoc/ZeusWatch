package com.sysadmindoc.nimbus.data.api

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import com.sysadmindoc.nimbus.data.model.WeatherCacheEntity

@Database(
    entities = [WeatherCacheEntity::class, SavedLocationEntity::class],
    version = 2,
    exportSchema = false,
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
            }
        }
    }
}
