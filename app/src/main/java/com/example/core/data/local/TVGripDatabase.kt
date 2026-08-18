package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.model.ControllerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [TvDeviceEntity::class, ControllerProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TVGripDatabase : RoomDatabase() {
    abstract fun tvDeviceDao(): TvDeviceDao
    abstract fun controllerProfileDao(): ControllerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: TVGripDatabase? = null

        fun getDatabase(context: Context): TVGripDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TVGripDatabase::class.java,
                    "tvgrip_database.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val profileEntities = ControllerProfile.PRESET_PROFILES.map {
                                ControllerProfileEntity.fromDomain(it)
                            }
                            getDatabase(context).controllerProfileDao().insertProfiles(profileEntities)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
