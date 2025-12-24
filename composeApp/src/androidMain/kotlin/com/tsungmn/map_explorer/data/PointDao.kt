package com.tsungmn.map_explorer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PointDao {
    @Insert
    suspend fun insert(point: PointEntity)

    @Query("SELECT * FROM location_points ORDER BY timestamp ASC")
    suspend fun getAll(): List<PointEntity>

    @Query("DELETE FROM location_points")
    suspend fun clearAll()
}