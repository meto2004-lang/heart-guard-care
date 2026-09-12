package com.heartguard.watch.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_data")
data class HealthDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val heartRate: Int = 0,
    val heartRateStatus: Int = 0,
    val skinTemperature: Float = 0f,
    val ambientTemperature: Float = 0f,
    val temperatureStatus: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
