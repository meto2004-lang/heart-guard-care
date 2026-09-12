package com.heartguard.mobile.di

import android.content.Context
import androidx.room.Room
import com.heartguard.mobile.data.local.CaregiverDao
import com.heartguard.mobile.data.local.MobileDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MobileDatabase {
        return Room.databaseBuilder(
            context,
            MobileDatabase::class.java,
            "heart_guard_mobile_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideCaregiverDao(database: MobileDatabase): CaregiverDao {
        return database.caregiverDao()
    }
}
