package com.sysadmindoc.nimbus.data.api

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {

    @Query("SELECT * FROM saved_locations ORDER BY sortOrder ASC, addedAt ASC")
    fun getAllFlow(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAll(): List<SavedLocationEntity>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getById(id: Long): SavedLocationEntity?

    @Query("SELECT * FROM saved_locations WHERE isCurrentLocation = 1 LIMIT 1")
    suspend fun getCurrentLocation(): SavedLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocationEntity): Long

    @Update
    suspend fun update(location: SavedLocationEntity)

    @Delete
    suspend fun delete(location: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM saved_locations")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM saved_locations")
    suspend fun maxSortOrder(): Int?
}
