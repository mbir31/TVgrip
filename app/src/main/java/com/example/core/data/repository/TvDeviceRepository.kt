package com.example.core.data.repository

import com.example.core.data.local.TvDeviceDao
import com.example.core.data.local.TvDeviceEntity
import com.example.core.model.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TvDeviceRepository(private val dao: TvDeviceDao) {

    val allDevices: Flow<List<TvDevice>> = dao.getAllDevices().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getDeviceById(id: String): TvDevice? {
        return dao.getDeviceById(id)?.toDomain()
    }

    suspend fun getPreferredDevice(): TvDevice? {
        return dao.getPreferredDevice()?.toDomain()
    }

    suspend fun saveDevice(device: TvDevice) {
        dao.insertDevice(TvDeviceEntity.fromDomain(device))
    }

    suspend fun updateDevice(device: TvDevice) {
        dao.updateDevice(TvDeviceEntity.fromDomain(device))
    }

    suspend fun renameDevice(id: String, newName: String) {
        dao.renameDevice(id, newName)
    }

    suspend fun deleteDevice(id: String) {
        dao.deleteDeviceById(id)
    }

    suspend fun setPreferredDevice(id: String) {
        dao.setPreferredDevice(id)
    }
}
