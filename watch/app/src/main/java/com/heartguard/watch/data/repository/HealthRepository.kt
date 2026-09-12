package com.heartguard.watch.data.repository

import com.heartguard.watch.data.local.HealthDao
import com.heartguard.watch.data.local.entities.AlertEntity
import com.heartguard.watch.data.local.entities.HealthDataEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    private val healthDao: HealthDao
) {
    fun getLatestHealthData(): Flow<HealthDataEntity?> {
        return healthDao.getLatestHealthData()
    }

    fun getRecentHealthData(): Flow<List<HealthDataEntity>> {
        return healthDao.getRecentHealthData()
    }

    suspend fun insertHealthData(healthData: HealthDataEntity) {
        healthDao.insertHealthData(healthData)
    }

    fun getAllAlerts(): Flow<List<AlertEntity>> {
        return healthDao.getAllAlerts()
    }

    fun getUnacknowledgedAlerts(): Flow<List<AlertEntity>> {
        return healthDao.getUnacknowledgedAlerts()
    }

    suspend fun insertAlert(alert: AlertEntity) {
        healthDao.insertAlert(alert)
    }

    suspend fun acknowledgeAlert(alertId: String) {
        healthDao.acknowledgeAlert(alertId)
    }

    suspend fun cleanupOldData() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        healthDao.deleteOldAlerts(thirtyDaysAgo)
        healthDao.deleteOldHealthData(thirtyDaysAgo)
    }
}
