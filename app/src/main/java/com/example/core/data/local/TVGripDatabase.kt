package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.model.ControllerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [TvDeviceEntity::class, ControllerProfileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TVGripDatabase : RoomDatabase() {
    abstract fun tvDeviceDao(): TvDeviceDao
    abstract fun controllerProfileDao(): ControllerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: TVGripDatabase? = null

        /**
         * Adds the server certificate fingerprint used to pin the Android TV
         * server certificate after pairing.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tv_devices ADD COLUMN serverCertSha256 TEXT")
            }
        }

        fun getDatabase(context: Context): TVGripDatabase {
            return INSTANCE ?: synchronized(this) {
                var builtInstance: TVGripDatabase? = null
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TVGripDatabase::class.java,
                    "tvgrip_database.db"
                ).addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val profileEntities = ControllerProfile.PRESET_PROFILES.map {
                                    ControllerProfileEntity.fromDomain(it)
                                }
                                builtInstance?.controllerProfileDao()?.insertProfiles(profileEntities)
                            }
                        }
                    }).build()
                builtInstance = instance
                INSTANCE = instance
                instance
            }
        }
    }
}
