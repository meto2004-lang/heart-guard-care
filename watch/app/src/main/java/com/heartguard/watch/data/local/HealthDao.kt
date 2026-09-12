package com.heartguard.watch.data.local

import androidx.room.*
import com.heartguard.watch.data.local.entities.AlertEntity
import com.heartguard.watch.data.local.entities.HealthDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthData(healthData: HealthDataEntity)

    @Query("SELECT * FROM health_data ORDER BY timestamp DESC LIMIT 1")
    fun getLatestHealthData(): Flow<HealthDataEntity?>

    @Query("SELECT * FROM health_data ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHealthData(): Flow<List<HealthDataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE isAcknowledged = 0 ORDER BY timestamp DESC")
    fun getUnacknowledgedAlerts(): Flow<List<AlertEntity>>

    @Query("UPDATE alerts SET isAcknowledged = 1 WHERE id = :alertId")
    suspend fun acknowledgeAlert(alertId: String)

    @Query("DELETE FROM alerts WHERE timestamp < :timestamp")
    suspend fun deleteOldAlerts(timestamp: Long)

    @Query("DELETE FROM health_data WHERE timestamp < :timestamp")
    suspend fun deleteOldHealthData(timestamp: Long)
}
