package com.heartguard.watch.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val severity: String,
    val message: String,
    val heartRate: Int? = null,
    val temperature: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isAcknowledged: Boolean = false
)
