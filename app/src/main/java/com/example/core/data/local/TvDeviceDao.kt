package com.example.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TvDeviceDao {
    @Query("SELECT * FROM tv_devices ORDER BY lastConnectedAt DESC")
    fun getAllDevices(): Flow<List<TvDeviceEntity>>

    @Query("SELECT * FROM tv_devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: String): TvDeviceEntity?

    @Query("SELECT * FROM tv_devices WHERE isPreferred = 1 LIMIT 1")
    suspend fun getPreferredDevice(): TvDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: TvDeviceEntity)

    @Update
    suspend fun updateDevice(device: TvDeviceEntity)

    @Query("DELETE FROM tv_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: String)

    @Query("UPDATE tv_devices SET name = :newName WHERE id = :id")
    suspend fun renameDevice(id: String, newName: String)

    @Query("UPDATE tv_devices SET isPreferred = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPreferredDevice(id: String)
}
