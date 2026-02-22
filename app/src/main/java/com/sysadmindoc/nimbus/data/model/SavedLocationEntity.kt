package com.sysadmindoc.nimbus.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int = 0,
    val isCurrentLocation: Boolean = false, // GPS-based "My Location"
    val addedAt: Long = System.currentTimeMillis(),
)
