package com.example.core.data.repository

import com.example.core.data.local.ControllerProfileDao
import com.example.core.data.local.ControllerProfileEntity
import com.example.core.model.ControllerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ControllerProfileRepository(private val dao: ControllerProfileDao) {

    val allProfiles: Flow<List<ControllerProfile>> = dao.getAllProfiles().map { list ->
        if (list.isEmpty()) {
            ControllerProfile.PRESET_PROFILES
        } else {
            list.map { it.toDomain() }
        }
    }

    suspend fun getProfileById(id: String): ControllerProfile? {
        return dao.getProfileById(id)?.toDomain()
    }

    suspend fun getDefaultProfile(): ControllerProfile {
        return getProfileById("preset_classic") ?: ControllerProfile.DEFAULT_STANDARD
    }

    suspend fun saveProfile(profile: ControllerProfile) {
        dao.insertProfile(ControllerProfileEntity.fromDomain(profile))
    }

    suspend fun updateProfile(profile: ControllerProfile) {
        dao.updateProfile(ControllerProfileEntity.fromDomain(profile))
    }

    suspend fun deleteProfile(id: String) {
        dao.deleteProfileById(id)
    }
}
