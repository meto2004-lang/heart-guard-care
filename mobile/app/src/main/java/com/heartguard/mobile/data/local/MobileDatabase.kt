package com.heartguard.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EmergencyContactEntity::class, AlertEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun caregiverDao(): CaregiverDao

    companion object {
        @Volatile
        private var INSTANCE: MobileDatabase? = null

        fun getDatabase(context: Context): MobileDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MobileDatabase::class.java,
                    "heart_guard_mobile_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
