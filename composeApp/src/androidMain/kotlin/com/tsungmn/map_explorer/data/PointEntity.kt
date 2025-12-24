package com.tsungmn.map_explorer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_points")
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)