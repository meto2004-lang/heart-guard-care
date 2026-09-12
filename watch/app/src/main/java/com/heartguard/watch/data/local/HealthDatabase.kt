package com.heartguard.watch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.heartguard.watch.data.local.entities.AlertEntity
import com.heartguard.watch.data.local.entities.HealthDataEntity

@Database(
    entities = [HealthDataEntity::class, AlertEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        fun getDatabase(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "heart_guard_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
