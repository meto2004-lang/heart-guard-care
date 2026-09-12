package com.heartguard.mobile.data.local

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
    val isRead: Boolean = false,
    val isHandled: Boolean = false
)
