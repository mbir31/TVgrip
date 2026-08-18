package com.example.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ControllerProfileDao {
    @Query("SELECT * FROM controller_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<ControllerProfileEntity>>

    @Query("SELECT * FROM controller_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ControllerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ControllerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfiles(profiles: List<ControllerProfileEntity>)

    @Update
    suspend fun updateProfile(profile: ControllerProfileEntity)

    @Query("DELETE FROM controller_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)
}
